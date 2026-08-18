package miau.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({EntityPlayerSP.class})
public interface IAccessorEntityPlayerSP {
  @Accessor("horseJumpPower")
  float getHorseJumpPower();

  @Accessor("horseJumpPower")
  void setHorseJumpPower(float power);

  @Accessor("horseJumpPowerCounter")
  int getHorseJumpPowerCounter();

  @Accessor("horseJumpPowerCounter")
  void setHorseJumpPowerCounter(int counter);
}
