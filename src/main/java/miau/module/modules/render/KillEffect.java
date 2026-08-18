package miau.module.modules.render;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;

public class KillEffect extends Module {

  private final BooleanProperty lightning = new BooleanProperty("Lightning", true);
  private final BooleanProperty blood = new BooleanProperty("Blood Explosion", true);
  private final BooleanProperty explosion = new BooleanProperty("Explosion", true);
  private final BooleanProperty fireSmoke = new BooleanProperty("Fire N Smoke", true);

  private EntityLivingBase target;
  private static final Minecraft mc = Minecraft.getMinecraft();

  public KillEffect() {
    super("KillEffect", false, true);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.target != null && !mc.theWorld.loadedEntityList.contains(this.target)) {
        if (this.lightning.getValue()) {
          final EntityLightningBolt entityLightningBolt =
              new EntityLightningBolt(mc.theWorld, target.posX, target.posY, target.posZ);
          mc.theWorld.addEntityToWorld((int) (-Math.random() * 100000), entityLightningBolt);

          mc.thePlayer.playSound("ambient.weather.thunder", 1.0f, 1.0f);
        }

        if (this.explosion.getValue()) {
          for (int i = 0; i <= 8; i++) {
            mc.effectRenderer.emitParticleAtEntity(target, EnumParticleTypes.FLAME);
          }

          mc.thePlayer.playSound("item.fireCharge.use", 1.0f, 1.0f);
        }

        if (this.blood.getValue()) {
          double startY = target.posY;
          double endY = target.posY + target.height + .4;
          double step = 0.4;
          for (int i = 0; i < 100; i++) {
            for (double y = startY; y <= endY; y += step) {
              mc.theWorld.spawnParticle(
                  EnumParticleTypes.BLOCK_CRACK,
                  target.posX,
                  y,
                  target.posZ,
                  0,
                  0,
                  0,
                  Block.getStateId(Blocks.redstone_block.getDefaultState()));
            }
          }

          for (double y = startY; y <= endY; y += step) {
            mc.thePlayer.playSound("dig.stone", 1.0f, 1.0f);
          }
        }

        if (this.fireSmoke.getValue()) {
          for (int i = 0; i < 25; i++) {
            double offsetX = (Math.random() - 0.5) * target.width * 1.5;
            double offsetZ = (Math.random() - 0.5) * target.width * 1.5;
            double offsetY = Math.random() * target.height;

            mc.theWorld.spawnParticle(
                EnumParticleTypes.FLAME,
                target.posX + offsetX,
                target.posY + offsetY,
                target.posZ + offsetZ,
                0,
                0.08,
                0);
            mc.theWorld.spawnParticle(
                EnumParticleTypes.SMOKE_LARGE,
                target.posX + offsetX,
                target.posY + offsetY,
                target.posZ + offsetZ,
                0,
                0.12,
                0);
          }
          mc.thePlayer.playSound("item.fireCharge.use", 1.0f, 0.6f);
        }

        this.target = null;
      }
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      target = (EntityLivingBase) event.getTarget();
    }
  }
}
