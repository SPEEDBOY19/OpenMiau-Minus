package miau.util;

import java.lang.reflect.Field;
import net.minecraft.init.Items;

public class FindItems {
  public static void print() {
    try {
      for (Field f : Items.class.getFields()) {
        System.out.println(f.getName() + " " + f.getType().getName());
      }
    } catch (Exception e) {
    }
  }
}
