package miau.module.modules.combat.killaura.target;

import java.util.ArrayList;
import java.util.List;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class TargetManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final KillAura killAura;

  public TargetManager(KillAura killAura) {
    this.killAura = killAura;
  }

  public List<EntityLivingBase> getValidTargets() {
    ArrayList<EntityLivingBase> targets = new ArrayList<>();
    if (mc.theWorld == null) return targets;

    for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityLivingBase) {
        EntityLivingBase livingBase = (EntityLivingBase) entity;
        if (this.isValidTarget(livingBase) && this.isInRange(livingBase)) {
          targets.add(livingBase);
        }
      }
    }
    return targets;
  }

  public AttackData findBestTarget(List<EntityLivingBase> targets) {
    if (targets.isEmpty()) return null;

    if (targets.stream().anyMatch(this::isInSwingRange)) {
      targets.removeIf(e -> !this.isInSwingRange(e));
    }
    if (targets.stream().anyMatch(this::isInAttackRange)) {
      targets.removeIf(e -> !this.isInAttackRange(e));
    }
    if (targets.stream().anyMatch(this::isPlayerTarget)) {
      targets.removeIf(e -> !this.isPlayerTarget(e));
    }

    targets.sort(
        (e1, e2) -> {
          int sortBase = 0;
          switch (this.killAura.sort.getValue()) {
            case 1:
              sortBase = Integer.compare(e1.hurtTime, e2.hurtTime);
              break;
            case 0:
              sortBase = Float.compare(TeamUtil.getHealthScore(e1), TeamUtil.getHealthScore(e2));
              break;
          }
          return sortBase != 0
              ? sortBase
              : Double.compare(
                  RotationUtil.distanceToEntity(e1), RotationUtil.distanceToEntity(e2));
        });

    if (this.killAura.mode.getValue() == 1 && targets.size() > 1) {
      targets.sort(
          (e1, e2) -> {
            LastAttackData data1 = this.killAura.targetMap.get(e1.getEntityId());
            LastAttackData data2 = this.killAura.targetMap.get(e2.getEntityId());
            double score1 = -((e1.getHealth() * 25.0D) + (data1 == null ? 0 : data1.getTime()));
            double score2 = -((e2.getHealth() * 25.0D) + (data2 == null ? 0 : data2.getTime()));
            return Double.compare(score1, score2);
          });
    }

    if (this.killAura.mode.getValue() == 1 && this.killAura.hitRegistered) {
      this.killAura.hitRegistered = false;
      this.killAura.switchTick = 0;
    }
    if (this.killAura.mode.getValue() == 0 || this.killAura.switchTick >= targets.size()) {
      this.killAura.switchTick = 0;
    }

    return new AttackData(targets.get(this.killAura.switchTick));
  }

  public boolean isValidTarget(EntityLivingBase entityLivingBase) {
    return this.isValid(entityLivingBase)
        && (this.killAura.throughWalls.getValue()
            || RotationUtil.rayTrace(entityLivingBase) == null);
  }

  public boolean isInRange(EntityLivingBase entityLivingBase) {
    double maxRange = this.killAura.attackRange.getValue();
    maxRange += this.killAura.expandRange;
    return RotationUtil.distanceToEntity(entityLivingBase) <= maxRange;
  }

  public boolean isInSwingRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase)
        <= (double) this.killAura.attackRange.getValue();
  }

  public boolean isBoxInSwingRange(net.minecraft.util.AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB)
        <= (double) this.killAura.attackRange.getValue();
  }

  public boolean isInAttackRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase)
        <= (double) this.killAura.attackRange.getValue();
  }

  public boolean isBoxInAttackRange(net.minecraft.util.AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB)
        <= (double) this.killAura.attackRange.getValue();
  }

  public boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
    return entityLivingBase instanceof net.minecraft.entity.player.EntityPlayer
        && TeamUtil.isTarget((net.minecraft.entity.player.EntityPlayer) entityLivingBase);
  }

  public boolean isValid(EntityLivingBase entityLivingBase) {
    if (entityLivingBase == null || mc.theWorld == null || mc.thePlayer == null) {
      return false;
    }
    if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
      return false;
    }
    if (entityLivingBase == mc.thePlayer || entityLivingBase == mc.thePlayer.ridingEntity) {
      return false;
    }
    if (entityLivingBase == mc.getRenderViewEntity()
        || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
      return false;
    }
    if (entityLivingBase.deathTime > 0) {
      return false;
    }
    if (entityLivingBase instanceof net.minecraft.client.entity.EntityOtherPlayerMP) {
      return this.isValidPlayer((net.minecraft.entity.player.EntityPlayer) entityLivingBase);
    }
    if (entityLivingBase instanceof net.minecraft.entity.boss.EntityDragon
        || entityLivingBase instanceof net.minecraft.entity.boss.EntityWither) {
      return this.killAura.targetBosses.getValue();
    }
    if (entityLivingBase instanceof net.minecraft.entity.monster.EntityMob
        || entityLivingBase instanceof net.minecraft.entity.monster.EntitySlime) {
      if (entityLivingBase instanceof net.minecraft.entity.monster.EntitySilverfish) {
        return this.killAura.targetSilverfish.getValue() && this.allowTeamColor(entityLivingBase);
      }
      return this.killAura.targetMobs.getValue();
    }
    if (entityLivingBase instanceof net.minecraft.entity.passive.EntityAnimal
        || entityLivingBase instanceof net.minecraft.entity.passive.EntityBat
        || entityLivingBase instanceof net.minecraft.entity.passive.EntitySquid
        || entityLivingBase instanceof net.minecraft.entity.passive.EntityVillager) {
      return this.killAura.targetAnimals.getValue();
    }
    if (entityLivingBase instanceof net.minecraft.entity.monster.EntityIronGolem) {
      return this.killAura.targetGolems.getValue() && this.allowTeamColor(entityLivingBase);
    }
    return false;
  }

  private boolean isValidPlayer(net.minecraft.entity.player.EntityPlayer player) {
    if (!this.killAura.targetPlayers.getValue()) {
      return false;
    }
    boolean isInvisible = player.isInvisible();
    if (isInvisible && !this.killAura.targetInvisibles.getValue()) {
      return false;
    }
    if (TeamUtil.isFriend(player)) {
      return false;
    }
    return this.allowSameTeam(player)
        && (isInvisible || !miau.module.modules.misc.AntiBot.isBot(player));
  }

  private boolean allowTeamColor(EntityLivingBase entityLivingBase) {
    return this.killAura.targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase);
  }

  private boolean allowSameTeam(net.minecraft.entity.player.EntityPlayer player) {
    return this.killAura.targetTeams.getValue() || !TeamUtil.isSameTeam(player);
  }
}
