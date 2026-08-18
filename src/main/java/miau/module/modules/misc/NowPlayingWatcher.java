package miau.module.modules.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NowPlayingWatcher {

    private static final Logger LOGGER = Logger.getLogger("NowPlayingWatcher");

    private final File stateFile;
    private final long pollIntervalMs;
    private final boolean verbose;

    private Timer timer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    public NowPlayingWatcher() {
        this(defaultStatePath(), 1000L, false);
    }

    public NowPlayingWatcher(String stateFilePath, long pollIntervalMs, boolean verbose) {
        this.stateFile = new File(stateFilePath);
        this.pollIntervalMs = pollIntervalMs;
        this.verbose = verbose;
    }

    private static String defaultStatePath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }
        return appData + File.separator + "miau" + File.separator + "nowplaying.json";
    }

    public synchronized void start() {
        if (running.get()) return;

        if (!stateFile.getParentFile().exists()) {
            stateFile.getParentFile().mkdirs();
        }

        timer = new Timer("NowPlayingWatcher-Poll", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pollSafely();
            }
        }, 0, pollIntervalMs);

        running.set(true);
        log(Level.INFO, "Started watching: " + stateFile.getAbsolutePath());
    }

    public synchronized void stop() {
        if (!running.get()) return;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        running.set(false);
        NowPlayingHud.setActive(false);
        log(Level.INFO, "Stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void pollSafely() {
        try {
            poll();
            consecutiveErrors = 0;
        } catch (Exception e) {
            consecutiveErrors++;
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                NowPlayingHud.setActive(false);
            }
        }
    }

    private void poll() throws IOException {
        if (!stateFile.exists()) {
            if (NowPlayingHud.active) {
                NowPlayingHud.setActive(false);
            }
            return;
        }

        String content;
        try {
            content = new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // File đang bị ghi đè, bỏ qua chu kỳ này thử lại sau
        }

        if (content == null || content.trim().isEmpty()) return;

        JsonObject json;
        try {
            // Sử dụng cú pháp tĩnh parse chuẩn của Gson tương thích tốt 1.8.9
            json = new JsonParser().parse(content).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            return;
        }

        boolean active = json.has("active") && json.get("active").getAsBoolean();
        if (!active) {
            if (NowPlayingHud.active) {
                NowPlayingHud.setActive(false);
            }
            return;
        }

        String title = getStringOrDefault(json, "title", "Unknown");
        String platform = normalizePlatform(getStringOrDefault(json, "platform", "Unknown"));
        int positionSec = getIntOrDefault(json, "positionSeconds", 0);
        int durationSec = getIntOrDefault(json, "durationSeconds", 0);

        String currentTime = formatTime(positionSec);
        String totalTime = formatTime(durationSec);

        // Bơm dữ liệu trực tiếp vào HUD
        NowPlayingHud.setTrackInfo(title, currentTime, totalTime, platform);
    }

    private String normalizePlatform(String rawAppId) {
        String lower = rawAppId.toLowerCase();
        if (lower.contains("chrome") || lower.contains("msedge") || lower.contains("firefox")) {
            return "YouTube";
        }
        if (lower.contains("spotify")) {
            return "Spotify";
        }
        return rawAppId.isEmpty() ? "Unknown" : rawAppId;
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getStringOrDefault(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private int getIntOrDefault(JsonObject obj, String key, int def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void log(Level level, String message) {
        LOGGER.log(level, "[NowPlayingWatcher] " + message);
    }
}