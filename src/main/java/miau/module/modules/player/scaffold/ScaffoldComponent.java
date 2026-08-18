package miau.module.modules.player.scaffold;

import java.util.List;
import miau.event.impl.*;
import miau.property.Property;

public interface ScaffoldComponent {
  default List<Property<?>> getProperties() {
    return java.util.Collections.emptyList();
  }

  default void onUpdate(UpdateEvent event) {}

  default void onStrafe(StrafeEvent event) {}

  default void onMoveInput(MoveInputEvent event) {}

  default void onSafeWalk(SafeWalkEvent event) {}

  default void onLivingUpdate(LivingUpdateEvent event) {}

  default void onEnable() {}

  default void onDisable() {}

  default void onBlockPlaced() {}

  default void onRender3D(miau.event.impl.Render3DEvent event) {}
}
