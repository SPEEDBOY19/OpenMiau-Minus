package miau.module.modules.misc.killsults;

public class MiauTeamMode implements KillSultMode {
  @Override
  public String getMessage(String targetName) {
    return "LOL " + targetName + " GOT SNIPED BY MIAU TEAM ON YOUTUBE";
  }
}
