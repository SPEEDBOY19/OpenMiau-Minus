package miau.mixin;

import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({C03PacketPlayer.class})
public interface IAccessorC03PacketPlayer {
  @Accessor("x")
  double getX();

  @Accessor("y")
  double getY();

  @Accessor("z")
  double getZ();

  @Accessor("onGround")
  boolean isOnGround();

  @Accessor("onGround")
  void setOnGround(boolean boolean1);
}
