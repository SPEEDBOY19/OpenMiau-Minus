package miau.module.modules.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.module.modules.render.targethud.*;
import miau.property.properties.*;
import miau.util.player.TeamUtil;
import miau.util.render.RenderUtil;
import miau.util.time.TimerUtil;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class TargetHUD extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final TimerUtil lastAttackTimer = new TimerUtil();
  private EntityLivingBase lastTarget = null;
  private EntityLivingBase target = null;
  public EntityLivingBase renderEntity;

  public static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
  public static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
  public static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

  public TargetHUDMode[] targetHUDModes;

  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Raven", "Myau", "Exhibition", "Idleo", "Tirum"});
  public final ModeProperty ravenMode =
      new ModeProperty(
          "Raven Mode", 0, new String[] {"Modern", "Legacy"}, () -> this.mode.getValue() == 0);
  public final ModeProperty color =
      new ModeProperty(
          "Color", 0, new String[] {"DEFAULT", "HUD"}, () -> this.mode.getValue() == 1);
  public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
  public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
  public final DragProperty drag = new DragProperty("Position", new Vector2d(70, 30));

  {
    this.drag.render = true;
  }

  public final BooleanProperty showStatus = new BooleanProperty("Show win or loss", true);
  public final BooleanProperty healthColor = new BooleanProperty("Traditional health color", false);
  public final BooleanProperty renderEsp = new BooleanProperty("Render ESP", true);
  public final PercentProperty background =
      new PercentProperty("Background", 25, () -> this.mode.getValue() == 1);
  public final BooleanProperty head =
      new BooleanProperty("Head", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty indicator =
      new BooleanProperty("Indicator", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty outline =
      new BooleanProperty("Outline", false, () -> this.mode.getValue() == 1);
  public final BooleanProperty animations =
      new BooleanProperty("Animations", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty kaOnly = new BooleanProperty("KA only", true);
  public final BooleanProperty chatPreview = new BooleanProperty("Chat preview", false);

  public TargetHUD() {
    super("TargetHUD", false, true);
    targetHUDModes =
        new TargetHUDMode[] {
          new RavenMode(this), new MyauMode(this), new ExhibitionMode(this), new IdleoMode(this), new TirumMode(this)
        };
  }

  @Override
  public void onDisabled() {
    this.target = null;
    this.lastTarget = null;
    if (targetHUDModes != null) {
      ((RavenMode) targetHUDModes[0]).reset();
      ((MyauMode) targetHUDModes[1]).reset();
    }
  }

  @Override
  public void onEnabled() {
    if (targetHUDModes != null) {
      ((RavenMode) targetHUDModes[0]).reset();
      ((MyauMode) targetHUDModes[1]).reset();
    }
  }

  private EntityLivingBase resolveTarget() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura.isEnabled()
        && killAura.isAttackAllowed()
        && TeamUtil.isEntityLoaded(killAura.getTarget())) {
      return killAura.getTarget();
    } else if (!this.kaOnly.getValue()
        && !this.lastAttackTimer.hasTimeElapsed(1500L)
        && TeamUtil.isEntityLoaded(this.lastTarget)) {
      return this.lastTarget;
    } else {
      return (this.chatPreview.getValue()
              || mc.currentScreen instanceof net.minecraft.client.gui.GuiChat
              || mc.currentScreen instanceof miau.ui.clickgui.ClickGui)
          ? mc.thePlayer
          : null;
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    int modeVal = this.mode.getValue();
    boolean showChatPreview =
        chatPreview.getValue() && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat;

    if (modeVal == 1) {
      this.target = this.resolveTarget();
      if (this.target != null) {
        targetHUDModes[1].render(this.target, 0, 0);
      }
    } else if (modeVal >= 2 && modeVal < 4) {
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      if (killAura == null) return;

      EntityLivingBase killTarget = killAura.getTarget();
      if (killTarget != null && killAura.isEnabled()) {
        target = killTarget;
      } else if (showChatPreview) {
        target = mc.thePlayer;
      } else {
        return;
      }

      if (target instanceof EntityPlayer) {
        float x = (float) this.drag.position.x;
        float y = (float) this.drag.position.y;
        targetHUDModes[modeVal].render(target, x, y);
      }
    } else if (modeVal == 4) {
      this.target = this.resolveTarget();
      if (this.target != null && !this.target.isDead && !this.target.isInvisible()) {
        targetHUDModes[4].render(this.target, 0, 0);
      }
    } else {
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      if (killAura == null) return;

      RavenMode raven = (RavenMode) targetHUDModes[0];

      EntityLivingBase killTarget = killAura.getTarget();
      if (killTarget != null && killAura.isEnabled()) {
        target = killTarget;
        raven.setLastAliveMS(System.currentTimeMillis());
        raven.setFadeTimer(null);
      } else if (showChatPreview) {
        target = mc.thePlayer;
        raven.setLastAliveMS(System.currentTimeMillis());
        raven.setFadeTimer(null);
      } else if (target != null) {
        if (System.currentTimeMillis() - raven.getLastAliveMS() >= 400
            && raven.getFadeTimer() == null) {
          TimerUtil ft = new TimerUtil();
          ft.reset();
          raven.setFadeTimer(ft);
        }
      } else {
        return;
      }

      double health = target.getHealth() / target.getMaxHealth();
      if (target.isDead) {
        health = 0;
      }

      if (health != raven.getLastHealth()) {
        TimerUtil ht = new TimerUtil();
        ht.reset();
        raven.setHealthBarTimer(ht);
      }
      raven.setLastHealth(health);

      raven.render(target, 0, 0);
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {

    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

    if (!renderEsp.getValue() || !this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura == null) return;

    if (killAura.showTarget.getValue() != 0) return;

    EntityLivingBase espTarget = killAura.getTarget();
    if (espTarget != null && killAura.isEnabled()) {
      RenderUtil.renderEntity(espTarget, 2, 0.0, 0.0, -1, false);
    } else if (renderEntity != null) {
      RenderUtil.renderEntity(renderEntity, 2, 0.0, 0.0, -1, false);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
      C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
      if (packet.getAction() != Action.ATTACK) {
        return;
      }
      Entity entity = packet.getEntityFromWorld(mc.theWorld);
      if (entity instanceof EntityLivingBase) {
        if (entity instanceof EntityArmorStand) {
          return;
        }
        this.lastAttackTimer.reset();
        this.lastTarget = (EntityLivingBase) entity;
      }
    }
  }
}