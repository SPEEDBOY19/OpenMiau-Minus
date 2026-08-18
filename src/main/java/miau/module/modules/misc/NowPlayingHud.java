package miau.module.modules.misc;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class NowPlayingHud extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Static state variables for easy external updates
    public static boolean active = false;
    public static String currentSong = "Track Name";
    public static String timeInfo = "01:41 / 03:30";
    public static String platform = "YouTube";

    // Configuration
    public final BooleanProperty enabled = new BooleanProperty("Enabled", true);
    public final FloatProperty hudScale = new FloatProperty("HUD Scale", 1.0f, 0.5f, 2.0f);
    public final BooleanProperty showSpectrum = new BooleanProperty("Show Spectrum", true);

    private NowPlayingWatcher watcher;

    public NowPlayingHud() {
        super("NowPlayingHud", false);
    }

    @Override
    public void onEnabled() {
        watcher = new NowPlayingWatcher();
        watcher.start();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisabled() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRender2D(RenderGameOverlayEvent.Pre event) {
        if (!enabled.getValue()) return;
        if (!active) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        renderHud();
    }

    public void renderHud() {
        ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        float scale = hudScale.getValue();
        float baseX = 10f;
        float baseY = scaledHeight - 40f;

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0f);

        // Build text strings
        String prefix = "Now Playing : ";
        String trackText = currentSong + " | " + timeInfo + " [" + platform + "]";

        // Get fonts
        Font fontSmall = FontRepository.getFont("Inter Regular", 18f);
        Font fontMedium = FontRepository.getFont("Inter Medium", 18f);

        // Measure text widths
        float prefixWidth = fontMedium.getStringWidth(prefix);
        float trackWidth = fontSmall.getStringWidth(trackText);
        float totalTextWidth = prefixWidth + trackWidth;

        // Spectrum bars configuration
        int barCount = 4;
        float barWidth = 3f;
        float barSpacing = 2f;
        float spectrumWidth = barCount * barWidth + (barCount - 1) * barSpacing;
        float paddingRight = 8f;
        float paddingLeft = 10f;
        float paddingTop = 6f;
        float paddingBottom = 6f;

        float totalWidth = paddingLeft + totalTextWidth + spectrumWidth + paddingRight;
        float textHeight = Math.max(fontMedium.getFontHeight(), fontSmall.getFontHeight());
        float totalHeight = paddingTop + textHeight + paddingBottom;

        float x = baseX;
        float y = baseY;

        // Ensure HUD stays within screen bounds
        if (x + totalWidth > scaledWidth) {
            x = scaledWidth - totalWidth - 5f;
        }
        if (y - totalHeight < 0) {
            y = totalHeight + 5f;
        }

        // Draw glassmorphism background
        Color bgColor = new Color(10, 14, 20, 180);
        Color outlineColor = new Color(255, 255, 255, 20);
        RoundedUtils.drawRound(x, y - totalHeight, totalWidth, totalHeight, 5f, bgColor.getRGB());
        RoundedUtils.drawRoundOutline(x, y - totalHeight, totalWidth, totalHeight, 5f, 1f, bgColor, outlineColor);

        // Draw text
        float textY = y - totalHeight + paddingTop + (textHeight - fontMedium.getFontHeight()) / 2f;
        float textX = x + paddingLeft;

        // Draw prefix in theme color
        fontMedium.draw(prefix, textX, textY, new Color(0, 190, 245).getRGB(), false);
        textX += prefixWidth;

        // Draw track info in white
        fontSmall.draw(trackText, textX, textY, Color.WHITE.getRGB(), false);

        // Draw audio spectrum bars
        if (showSpectrum.getValue()) {
            float spectrumX = x + paddingLeft + totalTextWidth + 6f;
            float spectrumY = y - totalHeight + paddingTop;
            float maxBarHeight = textHeight;

            long currentTime = System.currentTimeMillis();

            for (int i = 0; i < barCount; i++) {
                // Calculate bar height using sine wave for smooth animation
                double angle = (currentTime * 0.003) + (i * 0.8);
                float sineValue = (float) Math.sin(angle);
                float normalizedHeight = (sineValue + 1.0f) / 2.0f; // 0.0 to 1.0
                float barHeight = 4f + normalizedHeight * (maxBarHeight - 4f);

                float barX = spectrumX + i * (barWidth + barSpacing);
                float barY = spectrumY + (maxBarHeight - barHeight);

                // Draw bar with gradient effect
                Color barColor = new Color(0, 190, 245, 220);
                RenderUtil.drawRoundedRectangle(barX, barY, barX + barWidth, barY + barHeight, 1.5f, barColor.getRGB());

                // Draw subtle top highlight
                Color highlightColor = new Color(0, 220, 255, 180);
                RenderUtil.drawRect(barX, barY, barX + barWidth, barY + 1f, highlightColor.getRGB());
            }
        }

        GL11.glPopMatrix();
    }

    // Helper method to update HUD data from external sources
    public static void setTrackInfo(String song, String currentTime, String totalTime, String platformName) {
        currentSong = song != null ? song : "Unknown";
        timeInfo = (currentTime != null ? currentTime : "00:00") + " / " + (totalTime != null ? totalTime : "00:00");
        platform = platformName != null ? platformName : "Unknown";
        active = true;
    }

    public static void clearTrack() {
        active = false;
        currentSong = "Track Name";
        timeInfo = "01:41 / 03:30";
        platform = "YouTube";
    }

    public static void setActive(boolean isActive) {
        active = isActive;
    }
}