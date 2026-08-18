package miau.mixin;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({S12PacketEntityVelocity.class})
public interface IAccessorS12PacketEntityVelocity {
  @Accessor("motionX")
  int getMotionX();

  @Accessor("motionY")
  int getMotionY();

  @Accessor("motionZ")
  int getMotionZ();

  @Accessor("motionX")
  void setMotionX(int motionX);

  @Accessor("motionY")
  void setMotionY(int motionY);

  @Accessor("motionZ")
  void setMotionZ(int motionZ);
}
