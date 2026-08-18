package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.TickEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class AntiBot extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty basic = new BooleanProperty("Basic", true);
  public final BooleanProperty matrixBot = new BooleanProperty("MatrixBot", false);

  private final Map<EntityPlayer, double[]> matrixSamples = new HashMap<>();
  private final Set<EntityPlayer> matrixNotAlwaysInRadius =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  private boolean matrixCollectSample;

  public AntiBot() {
    super("AntiBot", true, true);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    handleMatrixBot();
  }

  private void handleMatrixBot() {
    if (!matrixBot.getValue()) return;

    if (matrixNotAlwaysInRadius.size() > 1000) matrixNotAlwaysInRadius.clear();
    matrixSamples
        .keySet()
        .removeIf(
            player ->
                !mc.theWorld.loadedEntityList.contains(player)
                    || matrixNotAlwaysInRadius.contains(player));

    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) continue;

      EntityPlayer player = (EntityPlayer) entity;
      if (!isInMatrixCheckArea(player, 10.0F) && !matrixNotAlwaysInRadius.contains(player)) {
        matrixNotAlwaysInRadius.add(player);
        matrixSamples.remove(player);
      }
    }

    if (matrixCollectSample) {
      matrixSamples.clear();
      for (Entity entity : mc.theWorld.loadedEntityList) {
        if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) continue;

        EntityPlayer player = (EntityPlayer) entity;
        if (matrixNotAlwaysInRadius.contains(player)) continue;

        matrixSamples.put(player, new double[] {player.posX, player.posZ});
      }
    } else {
      List<EntityPlayer> bots = new ArrayList<>();
      for (Map.Entry<EntityPlayer, double[]> entry : matrixSamples.entrySet()) {
        EntityPlayer player = entry.getKey();
        double[] sample = entry.getValue();
        if (player == null
            || matrixNotAlwaysInRadius.contains(player)
            || !mc.theWorld.loadedEntityList.contains(player)) continue;

        double xDiff = sample[0] - player.posX;
        double zDiff = sample[1] - player.posZ;
        double speed = Math.sqrt(xDiff * xDiff + zDiff * zDiff) * 10.0;

        if (isMatrixBot(player, speed)) bots.add(player);
      }

      for (EntityPlayer bot : bots) {
        mc.theWorld.removeEntity(bot);
        matrixSamples.remove(bot);
      }
    }

    matrixCollectSample = !matrixCollectSample;
  }

  private boolean isMatrixBot(EntityPlayer player, double speed) {
    return player != mc.thePlayer
        && !matrixNotAlwaysInRadius.contains(player)
        && speed > 8.0
        && isInMatrixCheckArea(player, 5.0F);
  }

  private boolean isInMatrixCheckArea(EntityPlayer player, float radius) {
    return mc.thePlayer.getDistanceToEntity(player) <= radius
        && within(player.posY, mc.thePlayer.posY - 1.5, mc.thePlayer.posY + 1.5);
  }

  private double getHorizontalSpeed(EntityPlayer player) {
    double xDiff = player.posX - player.prevPosX;
    double zDiff = player.posZ - player.prevPosZ;
    return Math.sqrt(xDiff * xDiff + zDiff * zDiff) * 10.0;
  }

  private boolean within(double value, double min, double max) {
    return value >= min && value <= max;
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    clearAll();
  }

  private void clearAll() {
    matrixSamples.clear();
    matrixNotAlwaysInRadius.clear();
    matrixCollectSample = true;
  }

  @Override
  public void onDisabled() {
    clearAll();
  }

  public boolean isBotPlayer(EntityLivingBase entity) {
    if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) return false;
    if (!isEnabled()) return false;

    EntityPlayer player = (EntityPlayer) entity;
    int id = player.getEntityId();

    if (matrixBot.getValue() && isInvalidMatrixBotArmor(player)) return true;
    if (!basic.getValue()) return false;

    if (player.getUniqueID().version() == 2) return true;
    if (player.getDisplayName().getUnformattedText().contains("[NPC]")) return true;

    return player.getName().isEmpty() || player.getName().equals(mc.thePlayer.getName());
  }

  private boolean isInvalidMatrixBotArmor(EntityPlayer player) {
    ItemStack helmet = player.inventory.armorInventory[3];
    ItemStack chestplate = player.inventory.armorInventory[2];
    if (helmet == null || chestplate == null) return true;
    if (!(helmet.getItem() instanceof ItemArmor) || !(chestplate.getItem() instanceof ItemArmor))
      return true;

    int helmetColor = ((ItemArmor) helmet.getItem()).getColor(helmet);
    int chestplateColor = ((ItemArmor) chestplate.getItem()).getColor(chestplate);
    return !(chestplateColor > 0 && helmetColor > 0 && chestplateColor == helmetColor);
  }

  public static boolean isBot(EntityLivingBase entity) {
    AntiBot antiBot = (AntiBot) Miau.moduleManager.getModule(AntiBot.class);
    return antiBot != null && antiBot.isEnabled() && antiBot.isBotPlayer(entity);
  }

  public static boolean isBasicEnabled() {
    AntiBot antiBot = (AntiBot) Miau.moduleManager.getModule(AntiBot.class);
    return antiBot != null && antiBot.isEnabled() && antiBot.basic.getValue();
  }
}
