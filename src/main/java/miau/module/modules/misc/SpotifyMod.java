package miau.module.modules.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.function.BooleanSupplier;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.module.modules.render.WaterMark;
import miau.notification.NotificationType;
import miau.property.properties.DragProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.util.animation.Animation;
import miau.util.animation.Direction;
import miau.util.animation.impl.DecelerateAnimation;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import miau.util.shader.RoundedUtils;
import miau.util.spotify.LastFmAPI;
import miau.util.spotify.SpotifyAPI;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class SpotifyMod extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ModeProperty apiMode = new ModeProperty("API Mode", 1, new String[] {"LastFM", "Spotify"});
    private final TextProperty lastFmUser = new TextProperty("Username", "", () -> apiMode.getModeString().equals("LastFM"));
    private final TextProperty lastFmApiKey = new TextProperty("LASTFM API Key", "", () -> apiMode.getModeString().equals("LastFM"));
    private final TextProperty spotifyClientId = new TextProperty("Spotify Client ID", "", () -> apiMode.getModeString().equals("Spotify"));
    private final TextProperty spotifyClientSecret = new TextProperty("Spotify Client Secret", "", () -> apiMode.getModeString().equals("Spotify"));
    private final ModeProperty backgroundColor =
            new ModeProperty("Background", 0, new String[] {"Miau Minus", "Average", "Spotify Grey", "Sync"});

    private final DragProperty drag = new DragProperty("Spotify", new Vector2d(5, 150));

    public final float height = 50;
    public final float albumCoverSize = height;
    private final float playerWidth = 135;
    private final float width = albumCoverSize + playerWidth;

    private final Animation scrollTrack = new DecelerateAnimation(10000, 1, Direction.BACKWARDS);
    private final Animation scrollArtist = new DecelerateAnimation(10000, 1, Direction.BACKWARDS);

    public LastFmAPI api;
    public SpotifyAPI spotifyApi;
    private boolean downloadedCover;
    private ResourceLocation currentAlbumCover;
    private Color imageColor = Color.WHITE;
    private String lastDownloadedId = "";

    private final Color greyColor = new Color(30, 30, 30);

    // Mở rộng width một chút để chứa được text thời lượng dài (vd: 01:23 / 03:45)
    private static final float MIAU_FIXED_WIDTH = 175F;
    private static final float MIAU_MARQUEE_GAP = 40F;
    private static final String CJK_FONT_NAME = "notosanscjkkr-regular";

    private float miauAnimatedWidth = 130F;
    private long lastMiauRenderNano = 0L;
    private long miauMarqueeStart = 0L;
    private String lastMiauTitle = "";

    // Local Tracking Fallback (Dành cho trường hợp LastFM ko gửi progress)
    private String lastTrackNameLocal = "";
    private long trackStartTimeLocal = 0L;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File LASTFM_CREDS_DIR =
            new File(Minecraft.getMinecraft().mcDataDir, "Miau_LastFm.json");

    public SpotifyMod() {
        super("Spotify", false, true);
        this.drag.render = true;
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) {
            this.toggle();
            return;
        }

        if (apiMode.getModeString().equals("Spotify")) {
            String clientId = this.spotifyClientId.getValue();
            String clientSecret = this.spotifyClientSecret.getValue();

            if (spotifyApi == null) spotifyApi = new SpotifyAPI();

            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                loadCredentials();
                clientId = this.spotifyClientId.getValue();
                clientSecret = this.spotifyClientSecret.getValue();

                if (clientId.isEmpty() || clientSecret.isEmpty()) {
                    Miau.notificationManager.pop(
                            "Error", "Please input Spotify Client ID and Secret in settings", NotificationType.WARN);
                    this.toggle();
                    return;
                }
            }

            saveCredentials();
            spotifyApi.startConnection(clientId, clientSecret);
        } else {
            String user = this.lastFmUser.getValue();
            String key = this.lastFmApiKey.getValue();

            if (api == null) api = new LastFmAPI();

            if (user.equals("") || key.equals("")) {
                loadCredentials();
                user = this.lastFmUser.getValue();
                key = this.lastFmApiKey.getValue();

                if (user.equals("") || key.equals("")) {
                    Miau.notificationManager.pop(
                            "Error", "Please input Last.fm User and API Key in settings", NotificationType.WARN);
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop()
                                    .browse(new java.net.URI("https://idle.e-z.tools/p/b8tsrcndhv"));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    this.toggle();
                    return;
                }
            }

            saveCredentials();
            api.startConnection(user, key);
        }
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        if (spotifyApi != null) {
            spotifyApi.stopConnection();
        }
        super.onDisabled();
    }

    public boolean isPlaying() {
        return apiMode.getModeString().equals("Spotify") ? (spotifyApi != null && spotifyApi.isPlaying) : (api != null && api.isPlaying);
    }

    public String getTrackName() {
        return apiMode.getModeString().equals("Spotify") ? (spotifyApi != null ? spotifyApi.trackName : "Unknown") : (api != null ? api.trackName : "Unknown");
    }

    public String getArtistName() {
        return apiMode.getModeString().equals("Spotify") ? (spotifyApi != null ? spotifyApi.artistName : "Unknown") : (api != null ? api.artistName : "Unknown");
    }

    public String getAlbumUrl() {
        return apiMode.getModeString().equals("Spotify") ? (spotifyApi != null ? spotifyApi.albumUrl : "") : (api != null ? api.albumUrl : "");
    }

    public String getCurrentMbid() {
        return apiMode.getModeString().equals("Spotify") ? (spotifyApi != null ? spotifyApi.currentMbid : "") : (api != null ? api.currentMbid : "");
    }

    public static void scissor(double x, double y, double width, double height) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        final double scale = sr.getScaleFactor();
        y = sr.getScaledHeight() - y;
        x *= scale;
        y *= scale;
        width *= scale;
        height *= scale;
        GL11.glScissor((int) x, (int) (y - height), (int) width, (int) height);
    }

    @EventTarget
    public void onRender2DEvent(Render2DEvent event) {
        float x = (float) drag.position.x;
        float y = (float) drag.position.y;

        if (backgroundColor.getModeString().equals("Miau Minus")) {
            renderMiauCapsule();
        } else {
            if (!isPlaying()) return;
            drag.scale.x = width;
            drag.scale.y = height;
            renderClassicMode(x, y);
        }
    }

    private void renderClassicMode(float x, float y) {
        Color color2 = ColorUtil.darker(imageColor, 0.65f);

        switch (backgroundColor.getModeString()) {
            case "Average":
                float[] hsb =
                        Color.RGBtoHSB(imageColor.getRed(), imageColor.getGreen(), imageColor.getBlue(), null);
                if (hsb[2] < 0.5f) {
                    color2 = ColorUtil.brighter(imageColor, 0.65f);
                }
                RoundedUtils.drawRound(
                        x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, imageColor);
                break;
            case "Spotify Grey":
                RoundedUtils.drawRound(
                        x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, greyColor);
                break;
            case "Sync":
                RoundedUtils.drawRound(
                        x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, ColorUtil.rainbow(0));
                break;
        }

        Font font18 = FontRepository.getFont("inter-medium", 18f);
        Font font22 = FontRepository.getFont("inter-bold", 22f);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        scissor(x + albumCoverSize, y, playerWidth, height);

        String currentTrackName = getTrackName();
        String currentArtistName = getArtistName();

        if (scrollTrack.getDirection() == Direction.BACKWARDS && scrollTrack.getOutput() == 0) {
            scrollTrack.reset();
        }
        if (scrollArtist.getDirection() == Direction.BACKWARDS && scrollArtist.getOutput() == 0) {
            scrollArtist.reset();
        }
        boolean needsToScrollTrack = getMiauTextWidth(currentTrackName, font22, 22F) > playerWidth;
        boolean needsToScrollArtist = getMiauTextWidth(currentArtistName, font18, 18F) > playerWidth;

        float trackW = getMiauTextWidth(currentTrackName, font22, 22F);
        float trackX =
                (float)
                        (((x + albumCoverSize) - trackW)
                                + ((trackW + playerWidth) * scrollTrack.getOutput()));

        drawMiauText(currentTrackName, needsToScrollTrack ? trackX : x + albumCoverSize + 4, y + 8, -1, font22, 22F);

        float artistW = getMiauTextWidth(currentArtistName, font18, 18F);
        float artistX =
                (float)
                        (((x + albumCoverSize) - artistW)
                                + ((artistW + playerWidth) * scrollArtist.getOutput()));

        drawMiauText(
                currentArtistName, needsToScrollArtist ? artistX : x + albumCoverSize + 4, y + 26, -1, font18, 18F);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        downloadAlbumArt();

        if (currentAlbumCover != null && downloadedCover) {
            RenderUtil.resetColor();
            mc.getTextureManager().bindTexture(currentAlbumCover);
            GlStateManager.color(1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundTextured(x, y, albumCoverSize, albumCoverSize, 6, 1);
        }
    }

    private void renderMiauCapsule() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        long now = System.nanoTime();
        float deltaTime =
                this.lastMiauRenderNano == 0L
                        ? (1F / 60F)
                        : (float) ((now - this.lastMiauRenderNano) / 1_000_000_000D);
        deltaTime = Math.max(0F, Math.min(deltaTime, 0.1F));
        this.lastMiauRenderNano = now;

        WaterMark waterMark = getWaterMark();
        boolean playing = isPlaying();
        String title = playing ? getTrackName() + " - " + getArtistName() : "";

        if (!this.lastMiauTitle.equals(title)) {
            this.lastMiauTitle = title;
            this.miauMarqueeStart = System.currentTimeMillis();
        }

        Font titleFont = FontRepository.getFont("inter-medium", 14f);
        Font timeFont = FontRepository.getFont("inter-medium", 10f);
        String timeText = formatMiauTime(getMiauProgressMs(), getMiauDurationMs());
        float timeWidth = timeFont.width(timeText);

        float targetWidth = computeMiauTargetWidth(playing, title, titleFont, timeWidth);
        this.miauAnimatedWidth = miauLerpTowards(this.miauAnimatedWidth, targetWidth, 0.18F, deltaTime);
        float boxWidth = Math.max(40F, this.miauAnimatedWidth);

        float boxHeight = waterMark != null ? waterMark.getHeight() : 20F;

        float drawY = waterMark != null ? waterMark.getY() : 10F;
        float drawX;
        if (waterMark != null) {
            float leftAnchor = waterMark.getX() + waterMark.getWidth() + 6F;
            float centerX = leftAnchor + targetWidth / 2F;
            drawX = centerX - boxWidth / 2F;
        } else {
            drawX = new ScaledResolution(mc).getScaledWidth() / 2F - boxWidth / 2F;
        }

        this.drag.position.x = drawX;
        this.drag.position.y = drawY;
        this.drag.scale.x = boxWidth;
        this.drag.scale.y = boxHeight;

        float cornerRadius = boxHeight / 2F;
        float borderOffset = 1F;
        RoundedUtils.drawRound(
                drawX - borderOffset,
                drawY - borderOffset,
                boxWidth + (borderOffset * 2F),
                boxHeight + (borderOffset * 2F),
                cornerRadius + borderOffset,
                new Color(255, 255, 255, 200));
        RoundedUtils.drawRound(drawX, drawY, boxWidth, boxHeight, cornerRadius, new Color(15, 15, 15, 225));

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        scissor(drawX, drawY, boxWidth, boxHeight);
        drawMiauContent(
                drawX, drawY, boxWidth, boxHeight, playing, title, titleFont, timeFont, timeText, timeWidth);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private WaterMark getWaterMark() {
        if (Miau.moduleManager == null) return null;
        return (WaterMark) Miau.moduleManager.getModule(WaterMark.class);
    }

    private float computeMiauTargetWidth(boolean playing, String title, Font titleFont, float timeWidth) {
        if (playing) {
            return MIAU_FIXED_WIDTH;
        }
        float coverSize = 14F;
        float leftPart = 6F + coverSize + 6F;
        float rightPart = 6F;
        return leftPart + titleFont.width("No music playing") + rightPart;
    }

    private void drawMiauContent(
            float x,
            float y,
            float boxWidth,
            float boxHeight,
            boolean playing,
            String title,
            Font titleFont,
            Font timeFont,
            String timeText,
            float timeWidth) {
        float coverSize = 14F;
        float coverX = x + 6F;
        float coverY = y + (boxHeight - coverSize) / 2F;
        drawMiauCover(coverX, coverY, coverSize);

        float textX = coverX + coverSize + 6F;
        float textRight = x + boxWidth - 6F;
        float timeX = textRight - timeWidth;

        if (playing) {
            float titleAreaWidth = Math.max(0F, timeX - 6F - textX);
            float titleWidth = getMiauTextWidth(title, titleFont, 14F);

            if (titleWidth > titleAreaWidth && titleAreaWidth > 0F) {
                float scroll = updateMiauMarquee(titleWidth);
                scissor(textX, y, titleAreaWidth, boxHeight);
                drawMiauText(title, textX - scroll, y + 3F, Color.WHITE.getRGB(), titleFont, 14F);
                drawMiauText(
                        title,
                        textX - scroll + titleWidth + MIAU_MARQUEE_GAP,
                        y + 3F,
                        Color.WHITE.getRGB(),
                        titleFont,
                        14F);
                scissor(x, y, boxWidth, boxHeight);
            } else {
                drawMiauText(title, textX, y + 3F, Color.WHITE.getRGB(), titleFont, 14F);
            }

            float barY = y + boxHeight - 4F;
            float barH = 1.5F;
            float barWidth = Math.max(0F, timeX - 6F - textX);
            RoundedUtils.drawRound(textX, barY, barWidth, barH, 1F, new Color(255, 255, 255, 102));
            long duration = getMiauDurationMs();
            if (duration > 0L) {
                float fill = Math.max(0F, Math.min(1F, (float) getMiauProgressMs() / (float) duration));
                if (fill > 0.01F) {
                    RoundedUtils.drawRound(textX, barY, barWidth * fill, barH, 1F, new Color(255, 255, 255, 255));
                }
            }

            timeFont.draw(timeText, timeX, barY - 8F, new Color(255, 255, 255, 180).getRGB(), false);
        } else {
            titleFont.draw("No music playing", textX, y + (boxHeight / 2F) - 6F, Color.WHITE.getRGB(), false);
        }
    }

    private float updateMiauMarquee(float titleWidth) {
        long hold = 1200L;
        float speed = 30F;
        long elapsed = System.currentTimeMillis() - this.miauMarqueeStart;
        if (elapsed < hold) return 0F;
        float px = ((elapsed - hold) / 1000F) * speed;
        return px % (titleWidth + MIAU_MARQUEE_GAP);
    }

    private boolean hasNonAscii(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) return true;
        }
        return false;
    }

    private float getMiauTextWidth(String text, Font customFont, float cjkSize) {
        if (hasNonAscii(text)) {
            return FontRepository.getFont(CJK_FONT_NAME, cjkSize).width(text);
        }
        return customFont.width(text);
    }

    private void drawMiauText(String text, float x, float y, int color, Font customFont, float cjkSize) {
        if (hasNonAscii(text)) {
            FontRepository.getFont(CJK_FONT_NAME, cjkSize).draw(text, x, y, color, false);
        } else {
            customFont.draw(text, x, y, color, false);
        }
    }

    private float miauLerpTowards(float current, float target, float speed, float deltaTime) {
        float clamped = Math.max(0.0001F, Math.min(1F, speed));
        float t = 1F - (float) Math.pow(1D - (double) clamped, deltaTime * 60D);
        return current + (target - current) * t;
    }

    private void drawMiauCover(float x, float y, float size) {
        downloadAlbumArt();
        if (currentAlbumCover != null && downloadedCover) {
            RenderUtil.resetColor();
            mc.getTextureManager().bindTexture(currentAlbumCover);
            GlStateManager.color(1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundTextured(x, y, size, size, 4, 1);
        } else {
            drawMiauPlaceholder(x, y, size);
        }
    }

    private void drawMiauPlaceholder(float x, float y, float size) {
        try {
            RenderUtil.resetColor();
            mc.getTextureManager().bindTexture(new ResourceLocation("miau/logo.png"));
            GlStateManager.color(1F, 1F, 1F, 1F);
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundTextured(x, y, size, size, 4, 1);
            GlStateManager.disableBlend();
        } catch (Exception ignored) {
            RoundedUtils.drawRound(x, y, size, size, 4, new Color(30, 30, 30));
        }
    }

    private String formatMiauTime(long ms) {
        if (ms < 0L) ms = 0L;
        long totalSec = ms / 1000L;
        return String.format("%02d:%02d", totalSec / 60L, totalSec % 60L);
    }

    private String formatMiauTime(long elapsedMs, long durationMs) {
        String elapsed = formatMiauTime(elapsedMs);
        if (durationMs <= 0L) return elapsed;
        return elapsed + " / " + formatMiauTime(durationMs);
    }

    // --- System fix: Support API Reflection & Local Tracking Fallback ---

    private long getFieldAsLong(Object obj, String fieldName) throws Exception {
        Object val = obj.getClass().getField(fieldName).get(obj);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }

    private long getLocalProgress() {
        String currentTrack = getTrackName();
        if (currentTrack != null && !currentTrack.equals(lastTrackNameLocal)) {
            lastTrackNameLocal = currentTrack;
            trackStartTimeLocal = System.currentTimeMillis();
        }
        return System.currentTimeMillis() - trackStartTimeLocal;
    }

    private long getMiauProgressMs() {
        if (apiMode.getModeString().equals("Spotify")) {
            if (spotifyApi == null || !spotifyApi.isPlaying) return 0L;
            if (spotifyApi.lastPollMs > 0L && spotifyApi.durationMs > 0L) {
                return Math.min(
                        spotifyApi.durationMs,
                        spotifyApi.progressMs + (System.currentTimeMillis() - spotifyApi.lastPollMs));
            }
            return spotifyApi.progressMs > 0 ? spotifyApi.progressMs : getLocalProgress();
        } else {
            // Fallback an toàn cho LastFM
            if (api == null || !api.isPlaying) return 0L;
            try {
                long duration = getFieldAsLong(api, "durationMs");
                long progress = getFieldAsLong(api, "progressMs");
                long lastPoll = getFieldAsLong(api, "lastPollMs");

                if (lastPoll > 0L && duration > 0L) {
                    return Math.min(duration, progress + (System.currentTimeMillis() - lastPoll));
                }
                return progress > 0 ? progress : getLocalProgress();
            } catch (Exception e) {
                return getLocalProgress();
            }
        }
    }

    private long getMiauDurationMs() {
        if (apiMode.getModeString().equals("Spotify")) {
            if (spotifyApi == null) return 0L;
            return spotifyApi.durationMs > 0 ? spotifyApi.durationMs : 180000L; 
        } else {
            // Fallback an toàn cho LastFM
            if (api == null) return 0L;
            try {
                long duration = getFieldAsLong(api, "durationMs");
                return duration > 0 ? duration : 180000L; // Mặc định 3 phút nếu LastFM ko gửi duration
            } catch (Exception e) {
                return 180000L; // Mặc định 3 phút
            }
        }
    }

    private void downloadAlbumArt() {
        String currentMbid = getCurrentMbid();
        String albumUrl = getAlbumUrl();

        if (albumUrl == null || albumUrl.isEmpty()) {
            if (!lastDownloadedId.isEmpty()) {
                lastDownloadedId = "";
            }
            return;
        }

        String key =
                (currentMbid != null && !currentMbid.isEmpty())
                        ? currentMbid
                        : getTrackName() + "|" + getArtistName();

        if (!key.equals(lastDownloadedId)) {
            downloadedCover = false;
            lastDownloadedId = key;

            final ThreadDownloadImageData albumCover =
                    new ThreadDownloadImageData(
                            null,
                            albumUrl,
                            null,
                            new IImageBuffer() {
                                @Override
                                public BufferedImage parseUserSkin(BufferedImage image) {
                                    try {
                                        imageColor =
                                                averageColor(image, image.getWidth(), image.getHeight(), 1);
                                    } catch (Exception ignored) {}
                                    downloadedCover = true;
                                    return image;
                                }

                                @Override
                                public void skinAvailable() {}
                            });
            mc.getTextureManager()
                    .loadTexture(
                            currentAlbumCover =
                                    new ResourceLocation("lastfmAlbums/" + System.currentTimeMillis()),
                            albumCover);
        }
    }

    public void saveCredentials() {
        JsonObject keyObject = new JsonObject();
        keyObject.addProperty("user", this.lastFmUser.getValue());
        keyObject.addProperty("key", this.lastFmApiKey.getValue());
        keyObject.addProperty("spotify_client_id", this.spotifyClientId.getValue());
        keyObject.addProperty("spotify_client_secret", this.spotifyClientSecret.getValue());
        try {
            Writer writer = new BufferedWriter(new FileWriter(LASTFM_CREDS_DIR));
            GSON.toJson(keyObject, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCredentials() {
        JsonObject fileContent;
        try {
            fileContent = new JsonParser().parse(new FileReader(LASTFM_CREDS_DIR)).getAsJsonObject();
            if (fileContent.has("user")) {
                this.lastFmUser.setValue(fileContent.get("user").getAsString());
            }
            if (fileContent.has("key")) {
                this.lastFmApiKey.setValue(fileContent.get("key").getAsString());
            }
            if (fileContent.has("spotify_client_id")) {
                this.spotifyClientId.setValue(fileContent.get("spotify_client_id").getAsString());
            }
            if (fileContent.has("spotify_client_secret")) {
                this.spotifyClientSecret.setValue(fileContent.get("spotify_client_secret").getAsString());
            }
        } catch (FileNotFoundException e) {
        }
    }

    public static Color averageColor(BufferedImage image, int width, int height, int pixelStep) {
        int[] color = new int[3];
        int count = 0;
        for (int i = 0; i < width; i += pixelStep) {
            for (int j = 0; j < height; j += pixelStep) {
                Color pixel = new Color(image.getRGB(i, j));
                color[0] += pixel.getRed();
                color[1] += pixel.getGreen();
                color[2] += pixel.getBlue();
                count++;
            }
        }
        return new Color(color[0] / count, color[1] / count, color[2] / count);
    }
}