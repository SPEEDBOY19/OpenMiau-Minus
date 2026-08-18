package miau.management;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import miau.Miau;
import miau.module.Module;
import miau.module.modules.misc.RPC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public class DiscordRichPresence {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final int OP_HANDSHAKE = 0;
  private static final int OP_FRAME = 1;
  private static final int OP_CLOSE = 2;
  private static final String CLIENT_ID = "1533393088788168754";
  private static final String DEFAULT_DETAILS = "Miau Minus Client";
  private static final String DEFAULT_STATE = "";
  private static final String LARGE_IMAGE = "logo";
  private static final String LARGE_IMAGE_TEXT = "Miau Minus Client";
  private static final String SMALL_IMAGE = "steve";
  private static final String SMALL_IMAGE_TEXT = "Player";

  private RandomAccessFile pipe;
  private boolean running;
  private long startTimestamp;
  private long lastUpdate;
  private long nextStartAttempt;

  public boolean isRunning() {
    return this.running;
  }

  public void start(RPC rpc) {
    if (this.running || rpc == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now < this.nextStartAttempt) {
      return;
    }
    this.nextStartAttempt = now + 60000L;
    try {
      this.pipe = this.openPipe();
      this.running = true;
      this.startTimestamp = Instant.now().getEpochSecond();
      this.lastUpdate = 0L;
      this.write(OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + escape(CLIENT_ID) + "\"}");
      this.nextStartAttempt = 0L;
      this.update(rpc, true);
    } catch (Throwable ignored) {
      this.nextStartAttempt = System.currentTimeMillis() + 60000L;
      this.stop();
    }
  }

  public void stop() {
    this.running = false;
    if (this.pipe != null) {
      try {
        this.write(OP_CLOSE, "{}");
      } catch (Throwable ignored) {
      }
      try {
        this.pipe.close();
      } catch (IOException ignored) {
      }
      this.pipe = null;
    }
  }

  public void update(RPC rpc) {
    this.update(rpc, false);
  }

  private void update(RPC rpc, boolean force) {
    if (!this.running || this.pipe == null || rpc == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (!force && now - this.lastUpdate < 15000L) {
      return;
    }
    this.lastUpdate = now;

    try {
      String nonce = UUID.randomUUID().toString();
      String payload =
          "{"
              + "\"cmd\":\"SET_ACTIVITY\","
              + "\"args\":{"
              + "\"pid\":"
              + getPid()
              + ","
              + "\"activity\":"
              + this.buildActivity(rpc)
              + "},"
              + "\"nonce\":\""
              + nonce
              + "\""
              + "}";
      this.write(OP_FRAME, payload);
    } catch (Throwable ignored) {
      this.stop();
    }
  }

  private String buildActivity(RPC rpc) {
    String details = rpc.showServer.getValue() ? this.getServerText() : DEFAULT_DETAILS;
    String state = rpc.showModulesCount.getValue() ? this.getModulesText() : DEFAULT_STATE;

    StringBuilder builder = new StringBuilder();
    builder.append("{");
    builder.append("\"details\":\"").append(escape(details)).append("\",");
    if (!state.isEmpty()) {
      builder.append("\"state\":\"").append(escape(state)).append("\",");
    }
    builder.append("\"timestamps\":{\"start\":").append(this.startTimestamp).append("}");

    String smallImage = this.getSmallImage();
    String smallText = this.getSmallImageText();
    builder.append(",\"assets\":{");
    builder.append("\"large_image\":\"").append(escape(LARGE_IMAGE)).append("\"");
    builder.append(",\"large_text\":\"").append(escape(LARGE_IMAGE_TEXT)).append("\"");
    if (!smallImage.isEmpty()) {
      builder.append(",\"small_image\":\"").append(escape(smallImage)).append("\"");
      if (!smallText.isEmpty()) {
        builder.append(",\"small_text\":\"").append(escape(smallText)).append("\"");
      }
    }
    builder.append("}");
    builder.append("}");
    return builder.toString();
  }

  private String getServerText() {
    ServerData data = mc.getCurrentServerData();
    if (mc.isIntegratedServerRunning() || data == null) {
      return "Server: Singleplayer";
    }
    return "Server: " + data.serverIP;
  }

  private String getModulesText() {
    int enabled = 0;
    int total = 0;
    for (Module module : Miau.moduleManager.modules.values()) {
      total++;
      if (module.isEnabled()) {
        enabled++;
      }
    }
    return "Enabled " + enabled + " of " + total + " modules";
  }

  private RandomAccessFile openPipe() throws IOException {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      for (int i = 0; i < 10; i++) {
        try {
          return new RandomAccessFile(new File("\\\\.\\pipe\\discord-ipc-" + i), "rw");
        } catch (FileNotFoundException ignored) {
        }
      }
      throw new IOException("Discord IPC pipe not found");
    }

    String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
    String tmpDir = System.getProperty("java.io.tmpdir");
    String[] dirs = new String[] {runtimeDir, tmpDir, "/tmp"};
    for (String dir : dirs) {
      if (dir == null) continue;
      for (int i = 0; i < 10; i++) {
        File pipeFile = new File(dir, "discord-ipc-" + i);
        if (pipeFile.exists()) {
          return new RandomAccessFile(pipeFile, "rw");
        }
      }
    }
    throw new IOException("Discord IPC pipe not found");
  }

  private void write(int opCode, String json) throws IOException {
    byte[] data = json.getBytes(StandardCharsets.UTF_8);
    ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    header.putInt(opCode);
    header.putInt(data.length);
    this.pipe.write(header.array());
    this.pipe.write(data);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
  }

  private static long getPid() {
    String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    int index = name.indexOf('@');
    if (index > 0) {
      try {
        return Long.parseLong(name.substring(0, index));
      } catch (NumberFormatException ignored) {
      }
    }
    return 0L;
  }

  private String getSmallImage() {
    return SMALL_IMAGE;
  }

  private String getSmallImageText() {
    if (mc.thePlayer != null
        && mc.thePlayer.getName() != null
        && !mc.thePlayer.getName().trim().isEmpty()) {
      return mc.thePlayer.getName();
    }
    return SMALL_IMAGE_TEXT;
  }
}
