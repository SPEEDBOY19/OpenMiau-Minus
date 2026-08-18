package miau.event.impl;

import miau.event.callables.EventCancellable;
import miau.event.types.EventType;
import net.minecraft.entity.EntityLivingBase;

public class RenderLivingEvent extends EventCancellable {
  private final EventType type;
  private final EntityLivingBase entity;

  public RenderLivingEvent(EventType type, EntityLivingBase entityLivingBase) {
    this.type = type;
    this.entity = entityLivingBase;
  }

  public EventType getType() {
    return this.type;
  }

  public EntityLivingBase getEntity() {
    return this.entity;
  }
}
