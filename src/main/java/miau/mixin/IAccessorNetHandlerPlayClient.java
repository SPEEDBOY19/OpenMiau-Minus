package miau.mixin;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({NetHandlerPlayClient.class})
public interface IAccessorNetHandlerPlayClient {
  @Accessor("doneLoadingTerrain")
  boolean isDoneLoadingTerrain();
}
