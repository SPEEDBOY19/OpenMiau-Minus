package miau.mixin;

import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({C0FPacketConfirmTransaction.class})
public interface IAccessorC0FPacketConfirmTransaction {
  @Accessor("uid")
  short getUid();

  @Accessor("uid")
  void setUid(short uid);
}
