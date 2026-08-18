package miau.module.modules.render;

import java.awt.Color;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TargetMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty enabled = new BooleanProperty("Enabled", true);
    public final FloatProperty size = new FloatProperty("Size", 0.05F, 0.05F, 0.1F);
    public final FloatProperty rotationSpeed = new FloatProperty("RotationSpeed", 180.0F, 0.0F, 360.0F);
    public final ColorProperty color = new ColorProperty("Color", new Color(255, 255, 255, 150).getRGB());


    public final FloatProperty animSpeed = new FloatProperty("AnimSpeed", 60.0F, 1.0F, 240.0F);

    public final ModeProperty image = new ModeProperty("Image", 0, new String[]{
            "Target",
            "QuadStaple",
            "Flushed",
            "WitchDoctor",
            "Charizard",
            "WatermelonCat",
            "Hoshino",
            "BigLizard",
            "Moon_SalaryCat",
            "bigWitchDoctor",
            "Chinchilla",
            "Soggy",
            "Hutaooo",
            "Amonguspat",
            "Boykisser",
            "Moonsalarycat1",
            "Moonsalarycat2",
            "Burger",
            "Ricardo_milos"
    });

    private float rotation = 0.0F;

    public TargetMark() {
        super("TargetMark", false, true);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        EntityLivingBase target = getTarget();
        if (target != null) {
            double x = (target.lastTickPosX + (target.posX - target.lastTickPosX) * event.getPartialTicks()) - mc.getRenderManager().viewerPosX;
            double y = (target.lastTickPosY + (target.posY - target.lastTickPosY) * event.getPartialTicks()) + target.height * 0.6 - mc.getRenderManager().viewerPosY;
            double z = (target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * event.getPartialTicks()) - mc.getRenderManager().viewerPosZ;

            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(mc.getRenderManager().playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1), 1.0F, 0.0F, 0.0F);

            String selectedMode = image.getModeString();

            if (!selectedMode.equals("Target")) {
                rotation = 180.0F;
            } else {
                rotation += rotationSpeed.getValue() * (event.getPartialTicks() / 20.0F);
            }
            GL11.glRotatef(rotation % 360.0F, 0.0F, 0.0F, 1.0F);

            float finalSize = size.getValue() * 0.8F;
            GL11.glScalef(finalSize, finalSize, finalSize);

            drawTargetMark();

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

private void drawTargetMark() {
        String selectedMode = image.getModeString();
        ResourceLocation texture = getTextureForMode(selectedMode);

        int colorValue = color.getValue();
        float red = ((colorValue >> 16) & 0xFF) / 255.0F;
        float green = ((colorValue >> 8) & 0xFF) / 255.0F;
        float blue = (colorValue & 0xFF) / 255.0F;
        float alpha = ((colorValue >> 24) & 0xFF) / 255.0F;

        GL11.glPushMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        mc.getTextureManager().bindTexture(texture);

        if (isAnimatedMode(selectedMode)) {
            int totalFrames = getTotalFrames(selectedMode);

            int columns = 5;
            int frameSize = 240;

            // Tự động tính số hàng và chiều cao sheet từ tổng số frame thực tế của từng ảnh
            int rows = (int) Math.ceil((double) totalFrames / columns);
            float sheetWidth = columns * frameSize;
            float sheetHeight = rows * frameSize;

            float fps = Math.max(1.0F, animSpeed.getValue());
            long dead = (long) (1000.0F / fps);
            int currentFrame = (int) ((System.currentTimeMillis() / dead) % totalFrames);

            int col = currentFrame % columns;
            int row = currentFrame / columns;

            float u = col * frameSize;
            float v = row * frameSize;

            GL11.glColor4f(red, green, blue, alpha);
            Gui.drawScaledCustomSizeModalRect(-16, -16, u, v, frameSize, frameSize, 32, 32, sheetWidth, sheetHeight);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawScaledCustomSizeModalRect(-16, -16, u, v, frameSize, frameSize, 32, 32, sheetWidth, sheetHeight);

        } else {
            GL11.glColor4f(red, green, blue, alpha);
            Gui.drawModalRectWithCustomSizedTexture(-16, -16, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(-16, -16, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    // Kiểm tra mode có sử dụng Sprite Sheet animation theo frame
    private boolean isAnimatedMode(String mode) {
        return mode.equals("Moon_SalaryCat")
            || mode.equals("bigWitchDoctor")
            || mode.equals("Soggy")
            || mode.equals("Hutaooo")
            || mode.equals("Amonguspat")
            || mode.equals("Boykisser")
            || mode.equals("Moonsalarycat1")
            || mode.equals("Moonsalarycat2")
            || mode.equals("Burger")
            || mode.equals("Ricardo_milos");
    }

    // Cấu hình tổng số Frame thực tế của từng file ảnh Sprite Sheet
    private int getTotalFrames(String mode) {
        switch (mode) {
            case "bigWitchDoctor": return 36;
            case "Moon_SalaryCat": return 28;
            case "Ricardo_milos":  return 71;
            case "Moonsalarycat1": return 14; // 3 hàng x 5 cột
            case "Moonsalarycat2": return 18; // 4 hàng x 5 cột
            case "Amonguspat":     return 50; // 10 hàng x 5 cột
            case "Hutaooo":        return 5;  // 1 hàng x 5 cột
            case "Soggy":          return 24;
            case "Boykisser":      return 20;
            case "Burger":         return 20;
            default: return 20;
        }
}

    private ResourceLocation getTextureForMode(String mode) {
        switch (mode) {
            case "Target": return new ResourceLocation("miau/targetimage/target.png");
            case "QuadStaple": return new ResourceLocation("miau/targetimage/quadstaple.png");
            case "Flushed": return new ResourceLocation("miau/targetimage/flushed.png");
            case "WitchDoctor": return new ResourceLocation("miau/targetimage/witch_doctor.png");
            case "Charizard": return new ResourceLocation("miau/targetimage/charizard.png");
            case "WatermelonCat": return new ResourceLocation("miau/targetimage/watermeloncat.png");
            case "Hoshino": return new ResourceLocation("miau/targetimage/hoshino.png");
            case "BigLizard": return new ResourceLocation("miau/targetimage/biglizard.png");
            case "Moon_SalaryCat": return new ResourceLocation("miau/targetimage/Moon_SalaryCat.png");
            case "bigWitchDoctor": return new ResourceLocation("miau/targetimage/bigWitchDoctor.png");
            case "Chinchilla": return new ResourceLocation("miau/targetimage/chinchilla.png");
            case "Soggy": return new ResourceLocation("miau/targetimage/soggy.png");
            case "Hutaooo": return new ResourceLocation("miau/targetimage/hutaooo.png");
            case "Amonguspat": return new ResourceLocation("miau/targetimage/amonguspat.png");
            case "Boykisser": return new ResourceLocation("miau/targetimage/boykisser.png");
            case "Moonsalarycat1": return new ResourceLocation("miau/targetimage/moonsalarycat1.png");
            case "Moonsalarycat2": return new ResourceLocation("miau/targetimage/moonsalarycat2.png");
            case "Burger": return new ResourceLocation("miau/targetimage/burger.png");
            case "Ricardo_milos": return new ResourceLocation("miau/targetimage/ricardo-milos.png");
            default: return new ResourceLocation("miau/targetimage/target.png");
        }
    }

    private EntityLivingBase getTarget() {
        KillAura killAura = (KillAura) Miau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        return null;
    }
}