package miau.management;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;

public abstract class PlayerFileManager {
  public static Minecraft mc = Minecraft.getMinecraft();
  public ArrayList<String> players;
  public File file;
  public Color color;

  public PlayerFileManager(File file, Color color) {
    this.players = new ArrayList<>();
    this.file = file;
    this.color = color;
  }

  public void load() {
    if (!file.exists()) {
      try {
        if ((file.getParentFile().exists() || file.getParentFile().mkdirs())
            && file.createNewFile()) {
          System.out.printf("File created: %s%n", file.getName());
        }
      } catch (IOException e) {
        System.err.println("Error creating file: " + e.getMessage());
      }
    }
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      Set<String> loaded =
          reader
              .lines()
              .map(String::trim)
              .filter(name -> !name.isEmpty())
              .collect(Collectors.toCollection(LinkedHashSet::new));
      players.clear();
      players.addAll(loaded);
    } catch (IOException e) {
      System.err.println("Error reading file: " + e.getMessage());
    }
  }

  public void save() {
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
        writer.print(String.join("\n", players));
      }
    } catch (IOException e) {
      System.err.println("Error saving file: " + e.getMessage());
    }
  }

  public String add(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty() || isFriend(normalized)) {
      return null;
    }
    players.add(normalized);
    save();
    return normalized;
  }

  public String remove(String name) {
    if (name == null) {
      return null;
    }
    for (int i = 0; i < players.size(); i++) {
      String player = players.get(i);
      if (player.equalsIgnoreCase(name.trim())) {
        players.remove(i);
        save();
        return player;
      }
    }
    return null;
  }

  public void clear() {
    players.clear();
    save();
  }

  public boolean isFriend(String string) {
    return string != null
        && this.players.stream().anyMatch(string2 -> string2.equalsIgnoreCase(string.trim()));
  }

  public ArrayList<String> getPlayers() {
    return this.players;
  }

  public Color getColor() {
    return this.color;
  }
}
