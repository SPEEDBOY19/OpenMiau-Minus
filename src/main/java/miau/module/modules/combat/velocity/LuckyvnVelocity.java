package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class LuckyvnVelocity extends VelocityMode {
    private boolean shouldProcess = false;
    private boolean doJumpReset = false;
    private double reducedX = 0.0;
    private double reducedZ = 0.0;

    public LuckyvnVelocity(String name, Velocity parent) {
        super(name, parent);
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    double velX = packet.getMotionX() / 8000.0D;
                    double velZ = packet.getMotionZ() / 8000.0D;

                    reducedX = velX * 0.25;
                    reducedZ = velZ * 0.25;
                    doJumpReset = Math.random() < 0.75;
                    shouldProcess = true;
                    event.setCancelled(true);
                }
            }
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (shouldProcess) {
                mc.thePlayer.motionX = reducedX;
                mc.thePlayer.motionZ = reducedZ;
                if (doJumpReset && mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
                shouldProcess = false;
            }
        }
    }
}