package miau.module.modules.render.targethud;

import java.awt.Color;
import miau.module.modules.render.TargetHUD;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public class ExhibitionMode extends TargetHUDMode {

  public ExhibitionMode(TargetHUD parent) {
    super(parent);
  }

  @Override
  public void render(EntityLivingBase target, float x, float y) {
    if (target == null || target.isDead || target.isInvisible()) return;
    if (!(target instanceof EntityPlayer)) return;
    EntityPlayer player = (EntityPlayer) target;

    double[] screenPos = projectTo2D(target);
    if (screenPos == null) return;

    float distanceScale = calculateDistanceScale(target);
    float totalScale = distanceScale * parent.scale.getValue();

    double skeetW =
        mc.fontRendererObj.getStringWidth(player.getName()) > 70.0f
            ? (124.0f + mc.fontRendererObj.getStringWidth(player.getName()) - 70.0f)
            : 124.0;

    float posX = (float) (screenPos[0] / totalScale) - ((float) skeetW / 2.0f);
    float posY = (float) (screenPos[1] / totalScale) + 10.0f;

    GlStateManager.pushMatrix();
    GlStateManager.scale(totalScale, totalScale, 1.0F);
    GlStateManager.translate(posX, posY, 0f);

    skeetRect(0, -2.0, skeetW, 38.0, 1.0);
    skeetRectSmall(0.0f, -2.0f, 124.0f, 38.0f, 1.0);
    mc.fontRendererObj.drawString(player.getName(), (int) 42.3f, (int) 0.3f, -1);
    final float health = player.getHealth();
    final float healthWithAbsorption = player.getHealth() + player.getAbsorptionAmount();
    final float progress = health / player.getMaxHealth();
    final Color healthColor =
        health >= 0.0f
            ? blendColors(
                    new float[] {0.0F, 0.5F, 1.0F},
                    new Color[] {Color.RED, Color.YELLOW, Color.GREEN},
                    progress)
                .brighter()
            : Color.RED;
    double cockWidth = 50.0;
    final double healthBarPos = cockWidth * (double) progress;
    rectangle(42.5, 10.3, 103, 13.5, healthColor.darker().darker().darker().darker().getRGB());
    rectangle(42.5, 10.3, 53.0 + healthBarPos + 0.5, 13.5, healthColor.getRGB());
    if (player.getAbsorptionAmount() > 0.0f) {
      rectangle(
          97.5 - (double) player.getAbsorptionAmount(),
          10.3,
          103.5,
          13.5,
          new Color(137, 112, 9).getRGB());
    }
    rectangleBordered(42.0, 9.8f, 54.0 + cockWidth, 14.0, 0.5, 0, Color.BLACK.getRGB());
    for (int dist = 1; dist < 10; ++dist) {
      double cock = cockWidth / 8.5 * (double) dist;
      rectangle(43.5 + cock, 9.8, 43.5 + cock + 0.5, 14.0, Color.BLACK.getRGB());
    }
    GlStateManager.scale(0.5, 0.5, 0.5);
    final int distance = (int) mc.thePlayer.getDistanceToEntity(player);
    final String nice = "HP: " + (int) healthWithAbsorption + " | Dist: " + distance;
    mc.fontRendererObj.drawString(nice, 85.3f, 32.3f, -1, true);
    GlStateManager.scale(2.0, 2.0, 2.0);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableAlpha();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GL11.glPushMatrix();
    java.util.List<ItemStack> stuff = new java.util.ArrayList<>();
    int cock = -2;
    for (int i = 3; i >= 0; --i) {
      ItemStack armor = player.getCurrentArmor(i);
      if (armor != null) stuff.add(armor);
    }
    if (player.getHeldItem() != null) stuff.add(player.getHeldItem());
    for (ItemStack yes : stuff) {
      if (mc.theWorld != null) {
        RenderHelper.enableGUIStandardItemLighting();
        cock += 16;
      }
      GlStateManager.pushMatrix();
      GlStateManager.disableAlpha();
      GlStateManager.clear(256);
      GlStateManager.enableBlend();
      mc.getRenderItem().renderItemIntoGUI(yes, cock + 28, 20);
      mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, yes, cock + 28, 20);
      renderEnchantText(yes, cock + 28, (20 + 0.5f));
      GlStateManager.disableBlend();
      GlStateManager.scale(0.5, 0.5, 0.5);
      GlStateManager.disableDepth();
      GlStateManager.disableLighting();
      GlStateManager.enableDepth();
      GlStateManager.scale(2.0f, 2.0f, 2.0f);
      GlStateManager.enableAlpha();
      GlStateManager.popMatrix();
    }
    GL11.glPopMatrix();
    GlStateManager.disableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.scale(0.31, 0.31, 0.31);
    GlStateManager.translate(73.0f, 102.0f, 40.0f);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    drawModel(player.rotationYaw, player.rotationPitch, player);
    GlStateManager.popMatrix();
  }
}