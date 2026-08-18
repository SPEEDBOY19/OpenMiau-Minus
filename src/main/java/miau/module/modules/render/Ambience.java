package miau.module.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import org.lwjgl.opengl.GL11;

public final class Ambience extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final List<Particle> particles = new ArrayList<>();
  private EntityLivingBase currentTarget = null;
  private long lastHitTime = 0;

  public final IntProperty time = new IntProperty("Time", 0, 0, 22999);
  public final IntProperty speed = new IntProperty("Time Speed", 0, 0, 20);

  public final ModeProperty weather =
      new ModeProperty(
          "Weather",
          0,
          new String[] {
            "Unchanged", "Clear", "Rain", "Heavy Snow", "Light Snow", "Nether Particles"
          });

  public final ColorProperty snowColor =
      new ColorProperty(
          "Snow Color",
          Color.WHITE.getRGB(),
          () ->
              !weather.getModeString().equals("Heavy Snow")
                  && !weather.getModeString().equals("Light Snow"));

  // Cấu hình Particle Environment
  public final BooleanProperty particleEnvironment =
      new BooleanProperty("Particle Environment", false);
  public final ColorProperty particleColor =
      new ColorProperty(
          "Particle Color",
          new Color(0, 200, 255).getRGB(),
          particleEnvironment::getValue);
  public final IntProperty particleAmount =
      new IntProperty(
          "Particle Amount", 3, 1, 10, particleEnvironment::getValue);
  public final FloatProperty particleRadius =
      new FloatProperty(
          "Particle Radius", 1.2f, 0.5f, 3.0f, particleEnvironment::getValue);
  public final FloatProperty particleSpeed =
      new FloatProperty(
          "Particle Speed", 3.0f, 0.5f, 10.0f, particleEnvironment::getValue);

  public Ambience() {
    super("Ambience", false);
  }

  @Override
  public void onDisabled() {
    if (mc.theWorld != null) {
      mc.theWorld.setRainStrength(0);
      mc.theWorld.getWorldInfo().setCleanWeatherTime(Integer.MAX_VALUE);
      mc.theWorld.getWorldInfo().setRainTime(0);
      mc.theWorld.getWorldInfo().setThunderTime(0);
      mc.theWorld.getWorldInfo().setRaining(false);
      mc.theWorld.getWorldInfo().setThundering(false);
    }
    particles.clear();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!isEnabled() || event.getType() != EventType.PRE) return;

    if (particleEnvironment.getValue()) {
      EntityLivingBase target = getTarget();
      if (target != null && mc.thePlayer.ticksExisted % 2 == 0) {
        for (int i = 0; i < particleAmount.getValue(); i++) {
          particles.add(new Particle(target));
        }
      }
      particles.forEach(Particle::update);
      particles.removeIf(p -> p.age >= p.maxAge);
    } else {
      particles.clear();
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (mc.theWorld != null) {
      mc.theWorld.setWorldTime(
          (long) (time.getValue() + (System.currentTimeMillis() * speed.getValue())));
    }

    if (isEnabled() && particleEnvironment.getValue() && !particles.isEmpty()) {
      GL11.glPushMatrix();
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glDisable(GL11.GL_TEXTURE_2D);

      GL11.glEnable(GL11.GL_DEPTH_TEST);
      GL11.glDepthMask(false);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Glow additive blending cho sáng rực

      GL11.glDisable(GL11.GL_CULL_FACE);

      int colorValue = this.particleColor.getValue();
      float baseR = ((colorValue >> 16) & 0xFF) / 255.0F;
      float baseG = ((colorValue >> 8) & 0xFF) / 255.0F;
      float baseB = (colorValue & 0xFF) / 255.0F;

      for (Particle p : particles) {
        if (p.positions.size() > 1) {
          // 1. Vẽ phần đuôi sao chổi (Trail uốn lượn, to ở đầu và nhọn dần về đuôi)
          for (int i = 0; i < p.positions.size() - 1; i++) {
            double[] pos1 = p.positions.get(i);
            double[] pos2 = p.positions.get(i + 1);

            float x1 = (float) (pos1[0] - mc.getRenderManager().viewerPosX);
            float y1 = (float) (pos1[1] - mc.getRenderManager().viewerPosY);
            float z1 = (float) (pos1[2] - mc.getRenderManager().viewerPosZ);

            float x2 = (float) (pos2[0] - mc.getRenderManager().viewerPosX);
            float y2 = (float) (pos2[1] - mc.getRenderManager().viewerPosY);
            float z2 = (float) (pos2[2] - mc.getRenderManager().viewerPosZ);

            float progress = (float) i / p.positions.size(); // 0 ở đuôi, 1 ở gần đầu
            float alpha = progress * (1.0f - ((float) p.age / p.maxAge)) * 0.8f;

            // Đuôi nhọn mỏng, tiến dần về đầu thì to lên
            float dynamicWidth = 0.5f + progress * 2.2f;
            GL11.glLineWidth(dynamicWidth);

            GL11.glBegin(GL11.GL_LINES);
            // Màu pha trộn từ màu tùy chỉnh sang trắng mờ ở phần đầu sao chổi
            GL11.glColor4f(baseR * progress, baseG * progress, baseB * progress, alpha * 0.5f);
            GL11.glVertex3f(x1, y1, z1);
            GL11.glColor4f(baseR, baseG, baseB, alpha);
            GL11.glVertex3f(x2, y2, z2);
            GL11.glEnd();
          }

          // 2. Vẽ phần đầu sao chổi (Head) to tròn, sắc nét và có hiệu ứng glow trắng
          double[] headPos = p.positions.get(p.positions.size() - 1);
          float hx = (float) (headPos[0] - mc.getRenderManager().viewerPosX);
          float hy = (float) (headPos[1] - mc.getRenderManager().viewerPosY);
          float hz = (float) (headPos[2] - mc.getRenderManager().viewerPosZ);

          float headAlpha = (1.0f - ((float) p.age / p.maxAge));

          GL11.glPointSize(4.5f);
          GL11.glBegin(GL11.GL_POINTS);
          // Lớp nhân sáng trắng glow rực rỡ phần đầu
          GL11.glColor4f(1.0f, 1.0f, 1.0f, headAlpha * 0.9f);
          GL11.glVertex3f(hx, hy, hz);
          // Lớp quang phổ màu bao quanh đầu sao chổi
          GL11.glColor4f(baseR, baseG, baseB, headAlpha);
          GL11.glVertex3f(hx, hy, hz);
          GL11.glEnd();
        }
      }

      GL11.glLineWidth(1.0f);
      GL11.glEnable(GL11.GL_CULL_FACE);
      GL11.glDepthMask(true);
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glPopMatrix();
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (mc.thePlayer != null && mc.thePlayer.ticksExisted % 20 == 0) {
        switch (this.weather.getModeString()) {
          case "Clear":
            {
              mc.theWorld.setRainStrength(0);
              mc.theWorld.getWorldInfo().setCleanWeatherTime(Integer.MAX_VALUE);
              mc.theWorld.getWorldInfo().setRainTime(0);
              mc.theWorld.getWorldInfo().setThunderTime(0);
              mc.theWorld.getWorldInfo().setRaining(false);
              mc.theWorld.getWorldInfo().setThundering(false);
              break;
            }
          case "Nether Particles":
          case "Light Snow":
          case "Heavy Snow":
          case "Rain":
            {
              mc.theWorld.setRainStrength(1);
              mc.theWorld.getWorldInfo().setCleanWeatherTime(0);
              mc.theWorld.getWorldInfo().setRainTime(Integer.MAX_VALUE);
              mc.theWorld.getWorldInfo().setThunderTime(Integer.MAX_VALUE);
              mc.theWorld.getWorldInfo().setRaining(true);
              mc.theWorld.getWorldInfo().setThundering(false);
              break;
            }
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getPacket() instanceof S03PacketTimeUpdate) {
      event.setCancelled(true);
    } else if (event.getPacket() instanceof S2BPacketChangeGameState
        && !this.weather.getModeString().equals("Unchanged")) {
      S2BPacketChangeGameState s2b = (S2BPacketChangeGameState) event.getPacket();

      if (s2b.getGameState() == 1 || s2b.getGameState() == 2) {
        event.setCancelled(true);
      }
    }
  }

  public float getFloatTemperature(BlockPos blockPos, BiomeGenBase biomeGenBase) {
    if (this.isEnabled()) {
      switch (this.weather.getModeString()) {
        case "Nether Particles":
        case "Light Snow":
        case "Heavy Snow":
          return 0.1F;
        case "Rain":
          return 0.2F;
      }
    }
    return biomeGenBase.getFloatTemperature(blockPos);
  }

  public boolean skipRainParticles() {
    final String name = this.weather.getModeString();
    return this.isEnabled()
        && (name.equals("Light Snow")
            || name.equals("Heavy Snow")
            || name.equals("Nether Particles"));
  }

  private EntityLivingBase getTarget() {
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
      if (mc.thePlayer.isSwingInProgress) {
        currentTarget = (EntityLivingBase) mc.objectMouseOver.entityHit;
        lastHitTime = System.currentTimeMillis();
      }
    }

    if (currentTarget != null && !currentTarget.isDead && currentTarget.getHealth() > 0) {
      if (System.currentTimeMillis() - lastHitTime < 3000) {
        return currentTarget;
      }
    }

    KillAura killAura =
        (KillAura) Miau.moduleManager.getModule(KillAura.class);
    if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
      return killAura.getTarget();
    }

    return mc.thePlayer;
  }

  private class Particle {
    public double x, y, z;
    public int age, maxAge;
    private EntityLivingBase target;
    private double vx, vy, vz;
    private double angle;
    private double angleSpeed;
    public final List<double[]> positions = new ArrayList<>();

    public Particle(EntityLivingBase target) {
      this.target = target;
      this.age = 0;
      this.maxAge = 40 + (int) (Math.random() * 20);

      double range = 10.0;

      if (target == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) {
        Vec3 look = mc.thePlayer.getLookVec();
        this.x = target.posX + look.xCoord * 1.5 + (Math.random() - 0.5) * range;
        this.y = target.posY + mc.thePlayer.getEyeHeight() + (Math.random() - 0.5) * 1.5;
        this.z = target.posZ + look.zCoord * 1.5 + (Math.random() - 0.5) * range;
      } else {
        this.x = target.posX + (Math.random() - 0.5) * (range * 1.5);
        this.y = target.posY + Math.random() * target.height + (Math.random() - 0.5) * 3;
        this.z = target.posZ + (Math.random() - 0.5) * (range * 1.5);
      }

      double speed = particleSpeed.getValue() * 0.05;
      this.vx = (Math.random() - 0.5) * speed;
      this.vy = (Math.random() - 0.4) * speed * 0.7;
      this.vz = (Math.random() - 0.5) * speed;

      this.angle = Math.random() * Math.PI * 2;
      this.angleSpeed = (Math.random() - 0.5) * 0.15;
    }

    public void update() {
      this.age++;
      this.angle += this.angleSpeed;

      // Quỹ đạo lượn vòng quanh mục tiêu giống quỹ đạo sao chổi nhỏ
      this.vx += Math.cos(this.angle) * 0.02;
      this.vz += Math.sin(this.angle) * 0.02;

      this.x += this.vx;
      this.y += this.vy;
      this.z += this.vz;

      double range = 12.0;
      if (Math.abs(this.x - target.posX) > range) {
        this.vx *= -1;
      }
      if (Math.abs(this.z - target.posZ) > range) {
        this.vz *= -1;
      }
      if (this.y < target.posY - 0.5 || this.y > target.posY + target.height + 6) {
        this.vy *= -1;
      }

      positions.add(new double[]{this.x, this.y, this.z});
      if (positions.size() > 15) {
        positions.remove(0);
      }
    }
  }
}
