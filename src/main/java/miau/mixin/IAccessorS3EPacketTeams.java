package miau.mixin;

import java.util.Collection;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({S3EPacketTeams.class})
public interface IAccessorS3EPacketTeams {
  @Accessor("name")
  String getTeamName();

  @Accessor("players")
  Collection<String> getPlayers();
}
