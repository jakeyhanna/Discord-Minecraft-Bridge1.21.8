package org.discord.discordmaybe;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class DiscordMaybe implements ModInitializer, MessageForwarder {
    public static final Logger LOGGER = LogManager.getLogger("DiscordMaybe");
    private Discordbot bot;
    private MinecraftServer server;

    public static BotConfig config;

    @Override
    public void onInitialize() {
        config = BotConfig.load();

        String token = config.token;
        long channelId = config.channelId;

        // Keep a reference to the server + start the bot
        ServerLifecycleEvents.SERVER_STARTING.register(sv -> {
            this.server = sv;
            try {
                bot = new Discordbot(token, channelId, this);
                bot.start(); // usually async
                LOGGER.info("Discord bot starting…");
            } catch (Exception e) {
                LOGGER.error("Failed to start Discord bot", e);
            }
        });

        // Send the start embed when the server is actually ready
        ServerLifecycleEvents.SERVER_STARTED.register(sv -> {
            if (bot != null) {
                try {
                    bot.sendEmbedStartserver();
                } catch (Exception e) {
                    LOGGER.warn("Failed to send start message", e);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(sv -> {
            if (bot != null) {
                try {
                    bot.stop();
                } catch (Exception e) {
                    LOGGER.warn("Failed to stop Discord bot", e);
                }
            }
        });

        // ===== Player chat (plain messages) =====
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, params) -> {
            if (bot != null) {
                String discordMessage = player.getName().getString() + ": " + message.getContent().getString();
                bot.sendToDiscord(discordMessage);
            }
        });

        // ===== System messages (advancements + deaths) =====
        // We read the FINAL broadcasted TranslatableText, so this is locale-safe and exact.
        ServerMessageEvents.GAME_MESSAGE.register((sv, text, overlay) -> {
            if (!(text.getContent() instanceof TranslatableTextContent tr)) return;

            String key = tr.getKey();

            // --- Advancements: chat.type.advancement.{task,goal,challenge} ---
            if (key.startsWith("chat.type.advancement.")) {
                Text playerArg = argAsText(tr.getArgs(), 0); // decorated display name
                Text titleArg  = argAsText(tr.getArgs(), 1); // localized advancement title

                // Resolve the real player (handles team prefixes/suffixes)
                ServerPlayerEntity player = resolvePlayerFromMessageArg(sv, playerArg);

                String cleanName = (player != null) ? player.getGameProfile().name() : playerArg.getString();
                String uuid      = (player != null) ? player.getUuidAsString() : null;
                String advTitle  = titleArg.getString();

                if (bot != null) {
                    if (key.endsWith(".task")) {
                        bot.sendEmbedTaskDiscord(cleanName + " made the advancement: " + advTitle, uuid);
                    } else if (key.endsWith(".goal") || key.endsWith(".challenge")) {
                        bot.sendEmbedCompletedDiscord(cleanName + " completed: " + advTitle, uuid);
                    } else {
                        // Fallback (rare other subkeys)
                        bot.sendEmbedTaskDiscord(cleanName + " got: " + advTitle, uuid);
                    }
                }
                return; // handled
            }

            // --- Deaths: death.* (ALL vanilla death messages) ---
            if (key.startsWith("death.")) {
                // text.getString() is the EXACT line shown to players (localized, includes weapon, etc.)
                String fullMessage = text.getString();

                // arg[0] is usually the victim Text (decorated)
                Text victimArg = argAsText(tr.getArgs(), 0);
                ServerPlayerEntity victim = resolvePlayerFromMessageArg(sv, victimArg);

                String uuid = (victim != null) ? victim.getUuidAsString() : null;

                if (bot != null) {
                    bot.sendEmbedDeathDiscord(fullMessage, uuid);
                }
            }
        });

        // ===== Players joining and leaving =====
        ServerPlayerEvents.JOIN.register(player -> {
            if (bot == null) return;
            bot.sendEmbedjoinDiscord(player.getName().getString(), player.getUuidAsString());
        });

        ServerPlayerEvents.LEAVE.register(player -> {
            if (bot == null) return;
            bot.sendEmbedleaveDiscord(player.getName().getString(), player.getUuidAsString());
        });

        // ===== /botsay command =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(
                    CommandManager.literal("botsay")
                            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        if (bot != null) {
                                            String msg = StringArgumentType.getString(ctx, "message");
                                            bot.sendToDiscord(msg);
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }

    // ===================== Helpers =====================

    /** Safer arg extraction (handles non-Text args as well). */
    private static Text argAsText(Object[] args, int idx) {
        if (args == null || idx < 0 || idx >= args.length) return Text.empty();
        Object a = args[idx];
        return (a instanceof Text t) ? t : Text.of(String.valueOf(a));
        // Note: we intentionally avoid hover parsing; mappings vary and we keep things simple.
    }

    /**
     * Resolve the real player behind a decorated Text (team prefixes/suffixes).
     * Strategy:
     *  1) Compare to server's displayName (already decorated)
     *  2) Rebuild decoration via Team.decorateName(team, name) and compare
     *  3) Fallback: does the string contain/endWith raw username
     */
    private static ServerPlayerEntity resolvePlayerFromMessageArg(MinecraftServer sv, Text playerArg) {
        String decoratedFromMsg = playerArg.getString();

        // 1) Exact match vs display name
        for (ServerPlayerEntity p : sv.getPlayerManager().getPlayerList()) {
            if (decoratedFromMsg.equals(p.getDisplayName().getString())) {
                return p;
            }
        }

        // 2) Rebuild from team decoration and compare
        for (ServerPlayerEntity p : sv.getPlayerManager().getPlayerList()) {
            Team team = p.getScoreboardTeam();
            Text expected = (team != null) ? Team.decorateName(team, p.getName()) : p.getName();
            if (decoratedFromMsg.equals(expected.getString())) {
                return p;
            }
        }

        // 3) Fallback: simple contains/endsWith raw username
        ServerPlayerEntity best = null;
        int bestLen = -1;
        for (ServerPlayerEntity p : sv.getPlayerManager().getPlayerList()) {
            String raw = p.getGameProfile().name();
            if ((decoratedFromMsg.endsWith(raw) || decoratedFromMsg.contains(raw)) && raw.length() > bestLen) {
                best = p;
                bestLen = raw.length();
            }
        }
        return best; // may be null; callers handle null UUID gracefully
    }

    /** Keep this around for general lookups by plain name (not used in GAME_MESSAGE path). */
    public String getUUIDfromname(String playername){
        if (server == null || playername == null || playername.isBlank()) return null;

        ServerPlayerEntity online = server.getPlayerManager().getPlayer(playername);
        if (online != null){
            return online.getUuidAsString();
        }

        //GameProfile profile = server.getUserCache().findByName(playername).orElse(null);
        GameProfile profile = server.getPlayerManager().getPlayer(playername).getGameProfile();
        if (profile != null && profile.id() != null){
            return profile.id().toString();
        }

        return null;
    }

    @Override
    public void forwardToMinecraft(String message) {
        if (server != null) {
            server.getPlayerManager().broadcast(Text.literal(message), false);
        }
    }
}
