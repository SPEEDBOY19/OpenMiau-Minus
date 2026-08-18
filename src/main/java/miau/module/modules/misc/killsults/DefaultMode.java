package miau.module.modules.misc.killsults;

import java.util.Random;

public class DefaultMode implements KillSultMode {
  private final String[] messages = {
    "Wow! My combo is Miau Minus!",
    "Why would someone as bad as you not use Miau Minus?",
    "Here's your ticket to spectator from Miau Minus!",
    "I see you're a pay to lose player, huh?",
    "Do you need some PvP advice? Well Miau Minus is all you need.",
    "Hey! Wise up, don't waste another day without Miau Minus.",
    "You didn't even stand a chance against Miau Minus.",
    "We regret to inform you that your free trial of life has unfortunately expired.",
    "RISE against other cheaters by getting Miau Minus!",
    "You can pay for that loss by getting Miau Minus.",
    "Remember to use hand sanitizer to get rid of bacteria like you!",
    "Hey, try not to drown in your own salt.",
    "Having problems with forgetting to left click? Miau Minus can fix it!",
    "Rise up today by getting Miau Minus!",
    "Get Miau Minus, you need it.",
    "how about you rise up to heaven by ending it",
    "Did you know 3FMC has banned 6346 players in the last 7 days."
  };
  private final Random random = new Random();

  @Override
  public String getMessage(String targetName) {
    return String.format(messages[random.nextInt(messages.length)], targetName);
  }
}
