package miau.property.properties;

import com.google.gson.JsonObject;
import miau.property.Property;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class DragProperty extends Property<Vector2d> {
  public Vector2d position;
  public Vector2d targetPosition;
  public Vector2d scale;
  public Vector2d lastScale;

  public Animation animationPosition;
  public Animation smoothAnimation;

  public ScaledResolution lastScaledResolution;
  public boolean render = true;
  public boolean structure = false;

  private static final Minecraft mc = Minecraft.getMinecraft();

  public DragProperty(String name, Vector2d defaultValue) {
    super(name, defaultValue, null);
    this.position = new Vector2d(defaultValue.x, defaultValue.y);
    this.targetPosition = new Vector2d(defaultValue.x, defaultValue.y);
    this.scale = new Vector2d(100, 100);
    this.lastScale = new Vector2d(-1, -1);
    this.animationPosition = new Animation(Easing.LINEAR, 600);
    this.smoothAnimation = new Animation(Easing.EASE_OUT_EXPO, 300);
    this.lastScaledResolution = new ScaledResolution(mc);
  }

  public DragProperty(String name, Vector2d defaultValue, boolean render) {
    this(name, defaultValue);
    this.render = render;
  }

  public DragProperty(String name, Vector2d defaultValue, boolean render, boolean structure) {
    this(name, defaultValue);
    this.render = render && !structure;
    this.structure = structure;
  }

  public void setScale(Vector2d scale) {
    this.scale = scale;
    if (lastScale.x == -1 && lastScale.y == -1) {
      this.lastScale = this.scale;
    }

    ScaledResolution scaledResolution = new ScaledResolution(mc);

    if (this.position.x > scaledResolution.getScaledWidth() / 2f) {
      this.targetPosition.x += this.lastScale.x - this.scale.x;
      this.position.x = targetPosition.x;
    }

    if (this.position.y > scaledResolution.getScaledHeight() / 2f) {
      this.targetPosition.y += this.lastScale.y - this.scale.y;
      this.position.y = targetPosition.y;
    }

    this.lastScale = scale;
    this.lastScaledResolution = scaledResolution;
  }

  @Override
  public String getValuePrompt() {
    return "";
  }

  @Override
  public String formatValue() {
    return position.x + "," + position.y;
  }

  @Override
  public boolean parseString(String string) {
    try {
      String[] split = string.split(",");
      this.position.x = Double.parseDouble(split[0]);
      this.position.y = Double.parseDouble(split[1]);
      this.targetPosition.x = this.position.x;
      this.targetPosition.y = this.position.y;
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean read(JsonObject jsonObject) {
    if (jsonObject.has(getName() + "_x") && jsonObject.has(getName() + "_y")) {
      this.position.x = jsonObject.get(getName() + "_x").getAsDouble();
      this.position.y = jsonObject.get(getName() + "_y").getAsDouble();
      this.targetPosition.x = this.position.x;
      this.targetPosition.y = this.position.y;
      return true;
    }
    return false;
  }

  @Override
  public void write(JsonObject jsonObject) {
    jsonObject.addProperty(getName() + "_x", this.position.x);
    jsonObject.addProperty(getName() + "_y", this.position.y);
  }
}
