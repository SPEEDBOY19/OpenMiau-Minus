package miau.util.player;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;

/** Port of Rise 6 SlotUtil.java. Provides block/tool/item finding utilities for hotbar slots. */
public final class SlotUtil {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static final List<Block> blacklist =
      Arrays.asList(
          Blocks.enchanting_table,
          Blocks.chest,
          Blocks.ender_chest,
          Blocks.trapped_chest,
          Blocks.anvil,
          Blocks.sand,
          Blocks.web,
          Blocks.torch,
          Blocks.crafting_table,
          Blocks.furnace,
          Blocks.waterlily,
          Blocks.dispenser,
          Blocks.stone_pressure_plate,
          Blocks.wooden_pressure_plate,
          Blocks.noteblock,
          Blocks.dropper,
          Blocks.tnt,
          Blocks.standing_banner,
          Blocks.wall_banner,
          Blocks.redstone_torch,
          Blocks.pumpkin);

  /**
   * Finds the best block in the hotbar for scaffolding. Checks isFullBlock, glass variants, TNT —
   * and excludes the blacklist. Also checks item stack size thresholds.
   *
   * <p>Matches Rise's SlotUtil.findBlock() exactly.
   *
   * @return hotbar slot index (0-8), or -1 if none found
   */
  public static int findBlock() {
    for (int i = 36; i < 45; i++) {
      final ItemStack item = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
      if (item != null
          && item.getItem() instanceof ItemBlock
          && (item.stackSize > 5 || item.stackSize > 20)) {
        final Block block = ((ItemBlock) item.getItem()).getBlock();
        if ((block.isFullBlock()
                || block instanceof BlockGlass
                || block instanceof BlockStainedGlass
                || block instanceof BlockTNT)
            && !blacklist.contains(block)) {
          return i - 36;
        }
      }
    }
    return -1;
  }

  /**
   * Finds the best tool (highest dig speed) for a given block position. Compares all hotbar slots
   * by their effective speed against the block.
   *
   * <p>Matches Rise's SlotUtil.findTool(BlockPos) exactly.
   *
   * @param blockPos position of the block to mine
   * @return hotbar slot index (0-8), or -1 if none found
   */
  public static int findTool(final BlockPos blockPos) {
    float bestSpeed = 1;
    int bestSlot = -1;

    final IBlockState blockState = mc.theWorld.getBlockState(blockPos);

    for (int i = 0; i < 9; i++) {
      final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);

      if (itemStack == null) continue;

      final float speed = itemStack.getStrVsBlock(blockState.getBlock());

      if (speed > bestSpeed) {
        bestSpeed = speed;
        bestSlot = i;
      }
    }

    return bestSlot;
  }

  /**
   * Finds the best sword in the hotbar by damage + sharpness level.
   *
   * <p>Matches Rise's SlotUtil.findSword() exactly.
   *
   * @return hotbar slot index (0-8), or -1 if none found
   */
  public static int findSword() {
    int bestDurability = -1;
    float bestDamage = -1;
    int bestSlot = -1;

    for (int i = 0; i < 9; i++) {
      final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);

      if (itemStack == null) continue;

      if (itemStack.getItem() instanceof ItemSword) {
        final ItemSword sword = (ItemSword) itemStack.getItem();

        final int sharpnessLevel =
            EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack);
        final float damage = sword.getDamageVsEntity() + sharpnessLevel * 1.25F;
        final int durability = sword.getMaxDamage();

        if (bestDamage < damage) {
          bestDamage = damage;
          bestDurability = durability;
          bestSlot = i;
        }

        if (damage == bestDamage && durability > bestDurability) {
          bestDurability = durability;
          bestSlot = i;
        }
      }
    }

    return bestSlot;
  }

  /**
   * Finds a specific item in the hotbar. If the item is null, returns the first empty slot.
   *
   * <p>Matches Rise's SlotUtil.findItem(Item) exactly.
   *
   * @param item the item to find, or null for empty slot
   * @return hotbar slot index (0-8), or -1 if not found
   */
  public static int findItem(final Item item) {
    for (int i = 0; i < 9; i++) {
      final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);

      if (itemStack == null) {
        if (item == null) {
          return i;
        }
        continue;
      }

      if (itemStack.getItem() == item) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Finds a specific block type in the hotbar.
   *
   * <p>Matches Rise's SlotUtil.findBlock(Block) exactly.
   *
   * @param block the block to find, or null for empty slot
   * @return hotbar slot index (0-8), or -1 if not found
   */
  public static int findBlock(final Block block) {
    for (int i = 0; i < 9; i++) {
      final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);

      if (itemStack == null) {
        if (block == null) {
          return i;
        }
        continue;
      }

      if (itemStack.getItem() instanceof ItemBlock
          && ((ItemBlock) itemStack.getItem()).getBlock() == block) {
        return i;
      }
    }

    return -1;
  }

  private SlotUtil() {}
}
