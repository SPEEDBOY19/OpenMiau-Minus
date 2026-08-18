package miau.port.dew;

import miau.Miau;
import miau.module.Module;
import miau.module.modules.movement.Speed;
import miau.module.modules.misc.MoveFix;
import miau.module.modules.render.HUD;
import java.util.HashMap;
import java.util.Map;

/**
 * Glue that faithfully mimics Dew's {@code DewCommon} static entry point, wiring Dew's Scaffold
 * port to Miau's actual modules and a ported RotationManager.
 */
public class DewCommon {
  public static final DewModuleManager moduleManager = new DewModuleManager();
  public static final DewRotationManager rotationManager = new DewRotationManager();
  public static final ScaffoldGlue scaffoldGlue = new ScaffoldGlue();

  static {
    moduleManager.register(SpeedModule.class, new SpeedModule());
    moduleManager.register(MoveFix.class, new MoveFix());
    moduleManager.register(Hud.class, new Hud());
    moduleManager.register(SafetySwitchv2000.class, new SafetySwitchv2000());
    moduleManager.register(TargetStrafe.class, new TargetStrafe());
    moduleManager.register(Aura.class, new Aura());
    moduleManager.register(RotRandomizer.class, new RotRandomizer());
    moduleManager.register(PingReach.class, new PingReach());
  }

  public static void bind(Class<?> dewClass, Module miauModule) {
    moduleManager.bindMiau(dewClass, miauModule);
  }

  public static void resolveBindings() {
    if (Miau.moduleManager == null) return;
    bind(SpeedModule.class, Miau.moduleManager.getModule(Speed.class));
    bind(MoveFix.class, Miau.moduleManager.getModule(MoveFix.class));
    bind(Hud.class, Miau.moduleManager.getModule(HUD.class));
  }

  public static void ensureResolved() {
    if (Miau.moduleManager != null && moduleManager.bound(SpeedModule.class) == null) {
      resolveBindings();
    }
  }
}
