package com.jackyon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class BotConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("discordbot-config.json");

    public String token = "";
    public long channelId = 0L;

    // 1 = No Chat Report enabled
    // 0 = Normal Minecraft chat
    public int noChatReport = 1;

    public String defaultJoinGreeting = "YEP YEP HORRAY!!!";
    public Map<String, String> joinGreetings = defaultJoinGreetings();

    private static Map<String, String> defaultJoinGreetings() {
        Map<String, String> greetings = new LinkedHashMap<>();
        greetings.put("0ce55a56-1225-4645-b3d9-ea1d8fc1c694", "ALSALAM ALIKUM ALIKUM ALSALAM");
        greetings.put("00f0b3c8-4120-4c58-a416-44f342e90fb0", "ZA DOM SPREMNI");
        greetings.put("746557f3-c24b-4058-9b9a-8e50eeefb1d2", "I love you Nati");
        greetings.put("4528d4cf-ea5d-4d6e-8ac2-d7c834535a8a", "SALAMANCA DIH\n SALAMANCA MOMMY\n SALAMANCA BLUD");
        return greetings;
    }

    public String getJoinGreeting(String uuid, String playerName) {
        String greeting = joinGreetings == null ? null : joinGreetings.get(uuid);
        if (greeting == null) {
            greeting = defaultJoinGreeting;
        }
        return greeting == null ? "" : greeting.replace("{player}", playerName);
    }


    public static BotConfig load() {

        BotConfig cfg = new BotConfig();

        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);

                BotConfig loaded =
                        new Gson().fromJson(json, BotConfig.class);

                if (loaded != null) {
                    cfg = loaded;
                    cfg.save();
                }

            } catch (IOException | JsonParseException | IllegalStateException e) {
                e.printStackTrace();
            }

        } else {

            cfg.save();
        }

        return cfg;
    }


    public void save() {

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer =
                         Files.newBufferedWriter(CONFIG_PATH)) {

                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(this, writer);
            }

            System.out.println(
                    "Config saved to " + CONFIG_PATH
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
