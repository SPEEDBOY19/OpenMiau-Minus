package miau.event;

import java.lang.annotation.*;
import miau.event.types.Priority;

/**
 * Marks a method so that the EventManager knows that it should be registered. The priority of the
 * method is also set with this.
 *
 * @author DarkMagician6
 * @see Priority
 * @since July 30, 2013
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTarget {
  byte value() default Priority.MEDIUM;
}
