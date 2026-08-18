package miau.module.modules.player;

import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.player.ItemUtil;
import miau.util.player.TeamUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class GhostHand extends Module {
  public final BooleanProperty teamsOnly = new BooleanProperty("team-only", true);
  public final BooleanProperty ignoreWeapons = new BooleanProperty("ignore-weapons", false);

  public GhostHand() {
    super("GhostHand", false);
  }

  public boolean shouldSkip(Entity entity) {
    return entity instanceof EntityPlayer
        && !TeamUtil.isBot((EntityPlayer) entity)
        && (!this.teamsOnly.getValue() || TeamUtil.isSameTeam((EntityPlayer) entity))
        && (!this.ignoreWeapons.getValue() || !ItemUtil.hasRawUnbreakingEnchant());
  }
}
