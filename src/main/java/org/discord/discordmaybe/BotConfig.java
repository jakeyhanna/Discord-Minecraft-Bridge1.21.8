package org.discord.discordmaybe;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.IOException;
import com.google.gson.Gson;

public class BotConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("discordbot-config.json");

    public String token = "";  // Default value
    public long channelId = 0L;  // Default value

    // Load the configuration or create a default one if the file doesn't exist
    public static BotConfig load() {
        BotConfig cfg = new BotConfig();

        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                cfg = new Gson().fromJson(json, BotConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // If the file doesn't exist, create it with default values
            cfg.save();  // Create the file with default values
        }

        return cfg;
    }

    // Save the configuration (to be called after editing)
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                new Gson().toJson(this, writer);
            }
            System.out.println("Config saved to " + CONFIG_PATH.toString());  // Debugging line
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
