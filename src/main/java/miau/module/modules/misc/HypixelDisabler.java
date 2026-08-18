package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.player.Scaffold;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.client.KeyBindUtil;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;

public class HypixelDisabler extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final int DEFAULT_SETBACKS = 20;
  private static final long JOIN_DELAY = 200L;
  private static final long DELAY = 0L;
  private static final long CHECK_DISABLED_TIME = 4000L;
  private static final long TIMEOUT = 12000L;
  private static final double MIN_OFFSET = 0.2;

  private long joinTime, lobbyTime, finished;
  private boolean awaitJoin, joinTick, awaitSetback, noRotate, awaitJump, awaitGround;
  private int setbackCount, airTicks, disablerAirTicks;
  private double minSetbacks, zOffset;
  private float savedYaw, savedPitch;
  private boolean waitForJump = true;
  private boolean hideProgress;
  private String text;
  private int dispWidth, dispHeight;
  private int width;

  public final IntProperty offset = new IntProperty("Offset", 0, -10, 10);
  public final BooleanProperty hideProgressValue = new BooleanProperty("Hide progress", false);
  public final BooleanProperty zeroZeroDisabler = new BooleanProperty("00 disabler", false);

  public HypixelDisabler() {
    super("HypixelDisabler", false);
  }

  @Override
  public void onDisabled() {
    this.resetVars();
    this.waitForJump = true;
  }

  private void resetVars() {
    if (this.noRotate) {
      this.setNoRotate(true);
    }
    this.awaitJoin = this.joinTick = this.awaitSetback = this.noRotate = this.awaitJump =
        this.awaitGround = false;
    this.minSetbacks = this.zOffset = this.lobbyTime = this.finished = this.setbackCount = 0;
    this.hideProgress = false;
    this.text = null;
  }

  @EventTarget
  public void onPreMotion(PlayerUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    long now = System.currentTimeMillis();

    if (mc.thePlayer.onGround) {
      this.airTicks = 0;
    } else {
      this.airTicks++;
    }

    if (this.zeroZeroDisabler.getValue()) {
      Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
      if (scaffold != null && scaffold.isEnabled()) {
        this.waitForJump = true;
      } else {
        double y = mc.thePlayer.posY - 0.5;
        net.minecraft.block.Block block =
            mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, Math.floor(y), mc.thePlayer.posZ))
                .getBlock();
        if (block instanceof BlockStairs
            || (block instanceof BlockSlab)) {
          this.waitForJump = true;
        } else {
          if (this.waitForJump && this.airTicks > 3) {
            this.waitForJump = false;
          }
          if (!this.waitForJump && mc.thePlayer.onGround && mc.thePlayer.posY % 1 == 0) {
            mc.thePlayer.posY += 1e-14;
          }
        }
      }
    }

    if (!this.awaitGround && !mc.thePlayer.onGround) {
      this.disablerAirTicks++;
    } else {
      this.awaitGround = false;
      this.disablerAirTicks = 0;
    }

    if (this.awaitJoin && now >= this.joinTime + JOIN_DELAY) {
      ItemStack item = mc.thePlayer.inventory.getStackInSlot(8);
      if ((item != null && item.getItem() == Items.bed) || this.isPit()) {
        NoRotate noRotateModule = (NoRotate) Miau.moduleManager.modules.get(NoRotate.class);
        if (noRotateModule != null
            && noRotateModule.isEnabled()
            && (this.isSkywars() || this.isPit())) {
          noRotateModule.setEnabled(false);
          this.noRotate = true;
        }
        this.awaitJoin = false;
        this.joinTick = true;
      }
    }

    if (this.awaitSetback) {
      this.hideProgress =
          this.hideProgressValue.getValue()
              || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat));
      this.text =
          "\u00a77running disabler \u00a7b"
              + this.round((now - this.lobbyTime) / 1000d, 1)
              + "s "
              + (int) this.round(100 * (this.setbackCount / this.minSetbacks), 0)
              + "%";
      ScaledResolution sr = new ScaledResolution(mc);
      this.dispWidth = sr.getScaledWidth();
      this.dispHeight = sr.getScaledHeight();
      this.width = mc.fontRendererObj.getStringWidth(this.text) / 2 - 2;
    } else {
      this.text = null;
    }

    if (this.finished != 0 && mc.thePlayer.onGround && now - this.finished > CHECK_DISABLED_TIME) {
      ChatUtil.display("&7[&dR&7] &adisabler enabled");
      this.finished = 0;
    }

    if (this.awaitJump && this.disablerAirTicks == 5) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
      this.awaitJump = false;
      this.minSetbacks = DEFAULT_SETBACKS + this.offset.getValue();
      this.savedYaw = mc.thePlayer.rotationYaw;
      this.lobbyTime = now;
      this.awaitSetback = true;
    }

    if (this.joinTick) {
      this.joinTick = false;
      ChatUtil.display("&7[&dR&7] running disabler...");
      if (mc.thePlayer.onGround || (mc.thePlayer.fallDistance < 0.3 && !this.isPit())) {
        this.awaitJump = true;
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
      } else {
        this.minSetbacks = DEFAULT_SETBACKS + this.offset.getValue();
        this.savedYaw = mc.thePlayer.rotationYaw;
        this.lobbyTime = now;
        this.awaitSetback = true;
      }
      return;
    }

    if (this.awaitSetback) {
      if (this.setbackCount >= this.minSetbacks) {
        ChatUtil.display(
            "&7[&dR&7] &afinished in &b"
                + this.round((now - this.lobbyTime) / 1000d, 1)
                + "&as, wait a few seconds...");
        this.resetVars();
        this.finished = now;
        return;
      } else if (this.lobbyTime != 0 && now - this.lobbyTime > TIMEOUT) {
        ChatUtil.display("&7[&dR&7] &cdisabler failed");
        this.resetVars();
        return;
      }
      if (now - this.lobbyTime > DELAY) {
        mc.thePlayer.rotationYaw = this.savedYaw;
        mc.thePlayer.rotationPitch = this.savedPitch;
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionY = 0;
        mc.thePlayer.motionZ = 0;
        if (this.isSkywars()) {
          this.zOffset = MIN_OFFSET * 0.7;
          if (mc.thePlayer.ticksExisted % 2 == 0) {
            this.zOffset *= -1;
          }
          mc.thePlayer.posZ += this.zOffset;
        } else {
          mc.thePlayer.posZ += (this.zOffset += MIN_OFFSET);
        }
      }
    }
  }

  @EventTarget
  public void onPacketReceived(PacketEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.RECEIVE
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    if (this.awaitSetback && event.getPacket() instanceof S08PacketPlayerPosLook) {
      this.setbackCount++;
      this.zOffset = 0;
    }
  }

  @EventTarget
  public void onPostPlayerInput(MoveInputEvent event) {
    if (!this.isEnabled() || !this.awaitSetback || mc.thePlayer == null) return;
    mc.thePlayer.movementInput.moveForward = 0;
    mc.thePlayer.movementInput.moveStrafe = 0;
    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    mc.thePlayer.movementInput.jump = false;
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (this.awaitSetback) {
      if (this.hideProgress || this.text == null) return;
      mc.fontRendererObj.drawStringWithShadow(
          this.text, this.dispWidth / 2.0f - this.width, this.dispHeight / 2.0f + 13, -1);
    }
  }

  @EventTarget
  public void onWorldJoin(LoadWorldEvent event) {
    if (this.isEnabled()) {
      this.joinTime = System.currentTimeMillis();
      if (this.awaitSetback) {
        ChatUtil.display("&7[&dR&7] &cdisabing disabler");
        this.resetVars();
      }
      this.awaitJoin = this.awaitGround = true;
    }
  }

  private boolean isSkywars() {
    List<String> sidebar = this.getScoreboardLines();
    return sidebar != null
        && ((sidebar.size() > 0 && this.strip(sidebar.get(0)).contains("SKYWARS"))
            || (sidebar.size() > 8 && this.strip(sidebar.get(8)).contains("SkyWars")));
  }

  private boolean isPit() {
    List<String> sidebar = this.getScoreboardLines();
    return sidebar != null
        && sidebar.size() > 0
        && this.strip(sidebar.get(0)).contains("THE HYPIXEL PIT");
  }

  private List<String> getScoreboardLines() {
    List<String> lines = new ArrayList<>();
    if (mc.theWorld == null) return lines;
    Scoreboard scoreboard = mc.theWorld.getScoreboard();
    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
    if (objective == null) return lines;
    List<Score> scores = new ArrayList<>(scoreboard.getSortedScores(objective));
    if (scores.size() > 15) {
      scores = scores.subList(0, 15);
    }
    for (Score score : scores) {
      lines.add(score.getPlayerName());
    }
    return lines;
  }

  private String strip(String text) {
    if (text == null) return "";
    return text.replaceAll("\u00a7[0-9a-fk-or]", "");
  }

  private double round(double value, int places) {
    double factor = Math.pow(10, places);
    return Math.round(value * factor) / factor;
  }

  private void setNoRotate(boolean enabled) {
    NoRotate noRotateModule = (NoRotate) Miau.moduleManager.modules.get(NoRotate.class);
    if (noRotateModule != null) {
      noRotateModule.setEnabled(enabled);
    }
  }
}
