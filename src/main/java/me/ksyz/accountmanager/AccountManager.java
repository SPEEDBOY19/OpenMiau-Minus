package me.ksyz.accountmanager;

import com.google.gson.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.utils.Nan0EventRegister;
import me.ksyz.accountmanager.utils.SSLUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class AccountManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final File file = new File(mc.mcDataDir, "openmiau.accounts.json");
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Logger LOGGER = Logger.getLogger(AccountManager.class.getName());

  public static final ArrayList<Account> accounts = new ArrayList<>();

  public static void init() {
    SSLUtils.getSSLContext();
    Nan0EventRegister.register(MinecraftForge.EVENT_BUS, new Events());

    if (!file.exists()) {
      try {
        if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
          if (file.createNewFile()) {
            LOGGER.info("Successfully created openmiau.accounts.json");
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Couldn't create openmiau.accounts.json", e);
      }
    }
  }

  public static void load() {
    accounts.clear();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      JsonElement json = new JsonParser().parse(reader);
      if (json != null && json.isJsonArray()) {
        JsonArray jsonArray = json.getAsJsonArray();
        for (JsonElement jsonElement : jsonArray) {
          if (!jsonElement.isJsonObject()) {
            continue;
          }
          JsonObject jsonObject = jsonElement.getAsJsonObject();
          accounts.add(
              new Account(
                  Optional.ofNullable(jsonObject.get("refreshToken"))
                      .map(JsonElement::getAsString)
                      .orElse(""),
                  Optional.ofNullable(jsonObject.get("accessToken"))
                      .map(JsonElement::getAsString)
                      .orElse(""),
                  Optional.ofNullable(jsonObject.get("username"))
                      .map(JsonElement::getAsString)
                      .orElse(""),
                  Optional.ofNullable(jsonObject.get("unban"))
                      .map(JsonElement::getAsLong)
                      .orElse(0L),
                  Optional.ofNullable(jsonObject.get("clientId"))
                      .map(JsonElement::getAsString)
                      .orElse(""),
                  Optional.ofNullable(jsonObject.get("scope"))
                      .map(JsonElement::getAsString)
                      .orElse(""),
                  Optional.ofNullable(jsonObject.get("type"))
                      .map(JsonElement::getAsString)
                      .orElse(Account.TYPE_PREMIUM)));
        }
      }
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.WARNING, "Couldn't find openmiau.accounts.json", e);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Couldn't load openmiau.accounts.json", e);
    }
  }

  public static void save() {
    try {
      JsonArray jsonArray = new JsonArray();
      for (Account account : accounts) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("refreshToken", account.getRefreshToken());
        jsonObject.addProperty("accessToken", account.getAccessToken());
        jsonObject.addProperty("username", account.getUsername());
        jsonObject.addProperty("unban", account.getUnban());
        jsonObject.addProperty("clientId", account.getClientId());
        jsonObject.addProperty("scope", account.getScope());
        jsonObject.addProperty("type", account.getType());
        jsonArray.add(jsonObject);
      }
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      try (PrintWriter printWriter = new PrintWriter(new FileWriter(file))) {
        printWriter.println(gson.toJson(jsonArray));
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Couldn't save openmiau.accounts.json", e);
    }
  }
}
