package com.jackyon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class BotConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("discordbot-config.json");

    public String token = "";
    public long channelId = 0L;

    // 1 = No Chat Report enabled
    // 0 = Normal Minecraft chat
    public int noChatReport = 1;


    public static BotConfig load() {

        BotConfig cfg = new BotConfig();

        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);

                BotConfig loaded =
                        new Gson().fromJson(json, BotConfig.class);

                if (loaded != null) {
                    cfg = loaded;
                }

            } catch (IOException e) {
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