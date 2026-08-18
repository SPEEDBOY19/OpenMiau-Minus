package miau.module.modules.minigames;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.module.modules.minigames.bedwarsutils.BedwarsComponent;
import miau.module.modules.minigames.bedwarsutils.features.EventTimersFeature;
import miau.module.modules.minigames.bedwarsutils.features.UpgradeHUDFeature;
import miau.property.Property;

/**
 * @author meowtils
 */
public class BedwarsUtils extends Module {
  private final List<BedwarsComponent> components = new ArrayList<>();

  public final UpgradeHUDFeature upgradeHUDFeature;
  public final EventTimersFeature eventTimersFeature;

  public BedwarsUtils() {
    super("BedwarsUtils", false, true);
    this.upgradeHUDFeature = new UpgradeHUDFeature(this);
    this.eventTimersFeature = new EventTimersFeature(this);

    this.components.add(this.upgradeHUDFeature);
    this.components.add(this.eventTimersFeature);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (BedwarsComponent component : components) {
      props.addAll(component.getProperties());
    }
    return props;
  }

  @Override
  public void onEnabled() {
    super.onEnabled();
    components.forEach(BedwarsComponent::onReset);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    components.forEach(c -> c.onPacket(event));
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    components.forEach(c -> c.onRender2D(event));
  }

  @EventTarget
  public void onLoadWorld(miau.event.impl.LoadWorldEvent event) {
    components.forEach(BedwarsComponent::onReset);
  }
}
