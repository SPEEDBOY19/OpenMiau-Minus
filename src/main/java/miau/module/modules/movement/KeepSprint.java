package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.HitSlowDownEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode =
            new ModeProperty("mode", 0, new String[] {"VANILLA", "CANCEL", "GRIM", "VULCAN"});
    public final PercentProperty slowdown = new PercentProperty("slowdown", 0);
    public final BooleanProperty groundOnly = new BooleanProperty("ground-only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("reach-only", false);
    public final IntProperty delayTicks =
            new IntProperty("delay-ticks", 2, 0, 10, () -> mode.getValue() == 2 || mode.getValue() == 3);
    public final IntProperty resendInterval =
            new IntProperty("resend-interval", 10, 1, 40, () -> mode.getValue() == 3);

    private int hitTicks = 0;
    private int packetTicks = 0;
    private boolean wasHit = false;

    public KeepSprint() {
        super("KeepSprint", false);
    }

    @Override
    public void onDisabled() {
        hitTicks = 0;
        packetTicks = 0;
        wasHit = false;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !this.shouldKeepSprint()) return;
        hitTicks = 0;
        wasHit = true;
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || !this.shouldKeepSprint()) return;
        if (mode.getValue() == 0 || mode.getValue() == 1 && !wasHit) return;

        if (wasHit) {
            hitTicks++;
            if (hitTicks > delayTicks.getValue() + 2) {
                wasHit = false;
            }
        }

        if (wasHit && hitTicks <= delayTicks.getValue() + 1) {
            float speed = MoveUtil.getSpeed() > 0 ? 1.0F : 0.0F;
            event.setForward(speed);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!this.isEnabled() || !this.shouldKeepSprint()) return;

        switch (mode.getValue()) {
            case 0:
                vanillaTick();
                break;
            case 2:
                grimTick();
                break;
            case 3:
                vulcanTick();
                break;
        }
    }

    private void vanillaTick() {
        if (!mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(true);
        }
    }

    private void grimTick() {
        if (!wasHit && !mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(true);
        }
    }

    private void vulcanTick() {
        packetTicks++;
        if (packetTicks >= resendInterval.getValue()) {
            if (!mc.thePlayer.isSprinting()) {
                mc.thePlayer.setSprinting(true);
            }
            packetTicks = 0;
        }
    }

    @EventTarget
    public void onHitSlowDown(HitSlowDownEvent event) {
        if (!this.isEnabled() || !this.shouldKeepSprint()) return;

        switch (mode.getValue()) {
            case 0:
                event.setSprint(true);
                double mult = 1.0 - this.slowdown.getValue().doubleValue() / 100.0;
                event.setSlowDown(0.6 + 0.4 * mult);
                break;
            case 1:
            case 2:
            case 3:
                event.setCancelled(true);
                break;
        }
    }

    public boolean shouldKeepSprint() {
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        }
        return !this.reachOnly.getValue()
                || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F))
                > 3.0;
    }
}