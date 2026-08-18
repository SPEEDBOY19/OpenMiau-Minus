package miau.module.modules.minigames.bedwarsutils;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.property.Property;

public interface BedwarsComponent {
  default List<Property<?>> getProperties() {
    return new ArrayList<>();
  }

  default void onReset() {}

  default void onPacket(PacketEvent event) {}

  default void onRender2D(Render2DEvent event) {}
}
