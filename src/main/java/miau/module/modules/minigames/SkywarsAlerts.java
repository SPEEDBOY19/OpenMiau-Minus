package miau.module.modules.minigames;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.notification.NotificationType;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;

/**
 * @author meowtils
 */
public class SkywarsAlerts extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ModeProperty alertType =
      new ModeProperty("Alert", 0, new String[] {"Chat", "Notification", "All"});
  private final ModeProperty soundMode =
      new ModeProperty("Ping sound", 0, new String[] {"All", "Important", "None"});
  private final FloatProperty cooldown = new FloatProperty("Cooldown", 10.0f, 1.0f, 30.0f);
  private final BooleanProperty showDistance = new BooleanProperty("Show distance", true);

  // Items
  private final BooleanProperty fireSword = new BooleanProperty("Fire Sword", true);
  private final BooleanProperty diamondSword = new BooleanProperty("Diamond Sword", true);
  private final BooleanProperty knockbackSword = new BooleanProperty("Knockback Sword", true);
  private final BooleanProperty knockbackRod = new BooleanProperty("Knockback Rod", true);
  private final BooleanProperty strengthPotion = new BooleanProperty("Strength Potion", true);
  private final BooleanProperty enderPearl = new BooleanProperty("Ender Pearl", true);
  private final BooleanProperty corruptPearl = new BooleanProperty("Corrupt Pearl", true);
  private final BooleanProperty warpPearl = new BooleanProperty("Time Warp Pearl", true);

  private static final Map<String, Map<String, Long>> COOLDOWNS = new HashMap<>();
  private static final Map<UUID, Set<Item>> HELD_ITEM_CACHE = new HashMap<>();

  public SkywarsAlerts() {
    super("SkywarsAlerts", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == null || player == mc.thePlayer || player.isDead) continue;

      if (Miau.friendManager != null && Miau.friendManager.isFriend(player.getName())) continue;
      if (TeamUtil.isSameTeam(player)) continue;

      ItemStack held = player.getHeldItem();
      UUID uuid = player.getUniqueID();
      String itemName = null;

      if (held == null) continue;

      if (held.getItem() == Items.ender_pearl
          && held.hasEffect()
          && getTooltipMatches(held, player, "Teleport back")
          && warpPearl.getValue()) {
        itemName = EnumChatFormatting.LIGHT_PURPLE + "Time Warp Pearl";
      } else if (held.getItem() == Items.ender_pearl
          && held.hasEffect()
          && corruptPearl.getValue()) {
        itemName = EnumChatFormatting.DARK_AQUA + "Corrupt Pearl";
      } else if (held.getItem() == Items.ender_pearl && enderPearl.getValue()) {
        itemName = EnumChatFormatting.DARK_PURPLE + "Ender Pearl";
        markHeld(uuid, Items.ender_pearl);
      } else if (held.getItem() == Items.diamond_sword && diamondSword.getValue()) {
        itemName = EnumChatFormatting.AQUA + "Diamond Sword";
        markHeld(uuid, Items.diamond_sword);
      } else if (held.getItem() instanceof ItemSword
          && held.hasEffect()
          && hasEnchantment(held, Enchantment.fireAspect.effectId)
          && fireSword.getValue()) {
        itemName = EnumChatFormatting.RED + "Fire Sword";
        markHeld(uuid, held.getItem());
      } else if ((held.getItem() == Items.fishing_rod
              || held.getItem() == Items.stick
              || held.getItem() == Items.blaze_rod)
          && held.hasEffect()
          && hasEnchantment(held, Enchantment.knockback.effectId)
          && knockbackRod.getValue()) {
        itemName = EnumChatFormatting.GOLD + "Knockback Rod";
        markHeld(uuid, held.getItem());
      } else if ((held.getItem() instanceof ItemSword || held.getItem() == Items.slime_ball)
          && held.hasEffect()
          && hasEnchantment(held, Enchantment.knockback.effectId)
          && knockbackSword.getValue()) {
        itemName = EnumChatFormatting.YELLOW + "Knockback Sword";
        markHeld(uuid, held.getItem());
      } else if (held.getItem() instanceof ItemPotion
          && getTooltipMatches(held, player, "Strength")
          && strengthPotion.getValue()) {
        itemName = EnumChatFormatting.DARK_RED + "Strength Potion";
        markHeld(uuid, held.getItem());
      }

      if (itemName != null) {
        if (!hasCooldown(player.getName(), itemName)) {
          alert(player, itemName);
          setCooldown(player.getName(), itemName);
        }
      }
    }
  }

  private boolean getTooltipMatches(ItemStack stack, EntityPlayer player, String match) {
    try {
      for (String s : stack.getTooltip(player, false)) {
        if (s.contains(match)) return true;
      }
    } catch (Exception e) {
    }
    return false;
  }

  private boolean hasEnchantment(ItemStack stack, int enchantId) {
    return EnchantmentHelper.getEnchantmentLevel(enchantId, stack) > 0;
  }

  private void alert(EntityPlayer player, String itemName) {
    int distanceToEntity = (int) player.getDistanceToEntity(mc.thePlayer);
    String rawItemName = EnumChatFormatting.getTextWithoutFormattingCodes(itemName).toLowerCase();
    String distanceText =
        this.showDistance.getValue()
            ? (EnumChatFormatting.GRAY
                + " ("
                + EnumChatFormatting.AQUA
                + distanceToEntity
                + "m"
                + EnumChatFormatting.GRAY
                + ")")
            : "";
    String text = player.getName() + EnumChatFormatting.GRAY + " has " + itemName;

    String mode = alertType.getModeString();
    if (mode.equals("Chat") || mode.equals("All")) {
      ChatUtil.display(text + distanceText);
    }

    if (mode.equals("Notification") || mode.equals("All")) {
      Miau.notificationManager.pop("SkywarsAlerts", text, NotificationType.WARN);
    }

    String sound = soundMode.getModeString();
    if (sound.equals("All")) {
      sound();
    } else if (sound.equals("Important")) {
      if (rawItemName.equalsIgnoreCase("ender pearl")
          || rawItemName.equalsIgnoreCase("diamond sword")
          || rawItemName.equalsIgnoreCase("knockback rod")
          || rawItemName.contains("strength")
          || rawItemName.contains("corrupt pearl")
          || rawItemName.contains("time warp pearl")) {
        sound();
      }
    }
  }

  private boolean hasCooldown(String playerName, String itemName) {
    long alertCooldown = (long) (this.cooldown.getValue() * 1000L);
    Map<String, Long> playerCooldowns = COOLDOWNS.get(playerName);
    if (playerCooldowns == null) return false;

    Long lastTime = playerCooldowns.get(itemName);
    return (lastTime != null && System.currentTimeMillis() - lastTime < alertCooldown);
  }

  private void setCooldown(String playerName, String itemName) {
    COOLDOWNS
        .computeIfAbsent(playerName, k -> new HashMap<>())
        .put(itemName, System.currentTimeMillis());
  }

  private void sound() {
    mc.thePlayer.playSound("random.orb", 1.0f, 1.0f);
  }

  private static void markHeld(UUID uuid, Item item) {
    if (item == null) return;
    HELD_ITEM_CACHE.computeIfAbsent(uuid, k -> new HashSet<>()).add(item);
  }

  @Override
  public void onDisabled() {
    COOLDOWNS.clear();
    HELD_ITEM_CACHE.clear();
  }
}
