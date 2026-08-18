package miau.util.client;

import miau.module.modules.misc.MouseRawInput;
import miau.util.animation.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.util.MouseHelper;

public class RawMouseHelper extends MouseHelper {
  @Override
  public void mouseXYChange() {
    int rawDeltaX = MouseRawInput.consumeDeltaX();
    int rawDeltaY = MouseRawInput.consumeDeltaY();

    if (rawDeltaX == 0 && rawDeltaY == 0) {
      super.mouseXYChange();
      return;
    }

    this.deltaX = rawDeltaX;
    this.deltaY = -rawDeltaY;
  }
}
