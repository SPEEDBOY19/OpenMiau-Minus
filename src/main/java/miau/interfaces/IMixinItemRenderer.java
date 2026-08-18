package miau.interfaces;

public interface IMixinItemRenderer {
  void setCancelUpdate(boolean cancel);

  void setCancelReset(boolean cancel);

  boolean isRenderItemInUse();

  void setRenderItemInUse(boolean renderItemInUse);
}
