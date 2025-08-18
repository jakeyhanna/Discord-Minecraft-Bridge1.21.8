package org.discord.discordmaybe;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.advancement.*;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class DiscordMaybe implements ModInitializer, MessageForwarder {
    public static final Logger LOGGER = LogManager.getLogger("DiscordMaybe");
    private Discordbot bot;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        String token = "Insert Token here";
        long channelId = insert channel id here;

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;
            try {
                bot = new Discordbot(token, channelId, this);
                bot.start();
                LOGGER.info("Discord bot started successfully!");
            } catch (Exception e) {
                LOGGER.error("Failed to start Discord bot", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (bot != null) {
                bot.stop();
            }
        });


        // Listen to Minecraft chat messages
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, params) -> {
            if (bot != null) {
                String discordMessage = String.format("%s: %s",
                        player.getName().getString(),
                        message.getContent().getString());
                bot.sendToDiscord(discordMessage);
            }
        });



        //death messages
        ServerMessageEvents.GAME_MESSAGE.register((minecraftServer, text, b) -> {
            String message = text.getString();
            String name = text.getString().split(" ")[0];

            if (message.contains("has made the")){
                bot.sendEmbedTaskDiscord(message,getUUIDfromname(name));
            }

            else if (message.contains("has completed the")){
                bot.sendEmbedCompletedDiscord(message,getUUIDfromname(name));
            }
            // message.contains("has completed the"){
            //    bot.sendEmbedMessageDiscord(message);
            else if(message.contains("died") ||
                    message.contains("was slain by") ||
                    message.contains("blew up") ||
                    message.contains("fell") ||
                    message.contains("drowned") ||
                    message.contains("burned") ||
                    message.contains("hit") ||
                    message.contains("withered")){
                //bot.sendEmbedMessageDiscord(message);
                bot.sendEmbedDeathDiscord(message,getUUIDfromname(name));
            }
                });
//Players joining and leaving the server

        ServerPlayerEvents.JOIN.register(player -> {
            String name = player.getName().getString();
            String Id = player.getUuid().toString();
            bot.sendEmbedjoinDiscord(name,Id);
        //    bot.sendToDiscord("**"+name+ " JOINED THE GAME**");
        });
        ServerPlayerEvents.LEAVE.register(player -> {
            String name = player.getName().getString();
            String Id = player.getUuid().toString();
            bot.sendEmbedleaveDiscord(name,Id);
        //    bot.sendToDiscord("**"+name+ " LEFT THE GAME**");
        });
    }

    public String getUUIDfromname(String playername){
        if (server == null) return null;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playername);
        if (player != null){
            return player.getUuid().toString();
        }

        GameProfile profile = server.getUserCache().findByName(playername).orElse(null);
        if (profile != null){
            return profile.getId().toString();
        }

        return null;
    }

    @Override
    public void forwardToMinecraft(String message) {
        if (server != null) {
            server.getPlayerManager().broadcast(
                    net.minecraft.text.Text.literal(message),
                    false
            );
        }
    }
}