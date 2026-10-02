package com.jackyon;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Discordbridge implements ModInitializer, MessageForwarder {

	public static final Logger LOGGER =
			LoggerFactory.getLogger("DiscordMaybe");

	private Discordbot bot;
	private MinecraftServer server;
	private static boolean noChatReportEnabled;
	public static BotConfig config;

	@Override
	public void onInitialize() {

		config = BotConfig.load();

		noChatReportEnabled = config.noChatReport == 1;

		final String token = config.token;
		final long channelId = config.channelId;

		// =========================================================
		// SERVER START / STOP
		// =========================================================

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			this.server = server;

			try {
				bot = new Discordbot(token, channelId, this, config);
				bot.start();

				LOGGER.info("Discord bot starting...");
			} catch (Exception e) {
				LOGGER.error("Failed to start Discord bot", e);
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (bot == null) {
				return;
			}

			try {
				bot.sendEmbedStartserver();
			} catch (Exception e) {
				LOGGER.warn("Failed to send server-start message", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (bot == null) {
				return;
			}

			try {
				bot.sendEmbedStopserver();
				bot.stop();
			} catch (Exception e) {
				LOGGER.warn("Failed to stop Discord bot", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			this.server = null;
			this.bot = null;
		});

		// =========================================================
// PLAYER CHAT
// =========================================================
//
// noChatReportEnabled = true:
//   - cancel normal signed player chat
//   - rebroadcast as a SYSTEM/SERVER message
//
// noChatReportEnabled = false:
//   - allow normal vanilla player chat
//
// Discord forwarding happens in BOTH modes.
// =========================================================

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
				(chatMessage, player, boundChatType) -> {

					String playerName =
							player.getName().getString();

					String rawMessage =
							chatMessage.decoratedContent().getString();

					// =================================================
					// SEND TO DISCORD
					// =================================================

					if (bot != null) {
						try {
							bot.sendToDiscord(
									playerName + ": " + rawMessage
							);
						} catch (Exception e) {
							LOGGER.warn(
									"Failed to forward chat message to Discord",
									e
							);
						}
					}

					// =================================================
					// NORMAL VANILLA CHAT MODE
					// =================================================

					if (!noChatReportEnabled) {

						/*
						 * Returning true means:
						 *
						 * Let Minecraft broadcast the original
						 * player chat message normally.
						 */
						return true;
					}

					// =================================================
					// SERVER / SYSTEM CHAT MODE
					// =================================================

					Component minecraftMessage =
							Component.literal(
									"<" + playerName + "> " + rawMessage
							);

					MinecraftServer currentServer = this.server;

					if (currentServer != null) {

						currentServer
								.getPlayerList()
								.broadcastSystemMessage(
										minecraftMessage,
										false
								);
					}

					/*
					 * Prevent Minecraft from broadcasting the
					 * original signed player chat message.
					 */
					return false;
				}
		);

		// =========================================================
		// PLAYER JOIN
		// =========================================================

		ServerPlayerEvents.JOIN.register(player -> {
			if (bot == null) {
				return;
			}

			bot.sendEmbedjoinDiscord(
					player.getName().getString(),
					player.getUUID().toString()
			);
		});

		// =========================================================
		// PLAYER LEAVE
		// =========================================================

		ServerPlayerEvents.LEAVE.register(player -> {
			if (bot == null) {
				return;
			}

			bot.sendEmbedleaveDiscord(
					player.getName().getString(),
					player.getUUID().toString()
			);
		});

		// =========================================================
		// /botsay <message>
		// =========================================================

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {

					dispatcher.register(
							Commands.literal("botsay")

									.then(
											Commands.argument(
															"message",
															StringArgumentType.greedyString()
													)
													.executes(context -> {

														if (bot != null) {

															String message =
																	StringArgumentType.getString(
																			context,
																			"message"
																	);

															bot.sendToDiscord(message);
														}

														return 1;
													})
									)
					);
				}
		);
	}

	// =============================================================
	// HELPERS
	// =============================================================

	/**
	 * Finds the UUID of an ONLINE player by name.
	 *
	 * Your previous offline lookup could throw a
	 * NullPointerException because it tried to call
	 * getGameProfile() on the same null player.
	 */
	public String getUUIDfromname(String playerName) {

		if (server == null
				|| playerName == null
				|| playerName.isBlank()) {

			return null;
		}

		for (ServerPlayer player :
				server.getPlayerList().getPlayers()) {

			if (player.getName()
					.getString()
					.equalsIgnoreCase(playerName)) {

				return player.getUUID().toString();
			}
		}

		return null;
	}

	@Override
	public void forwardComponentsToMinecraft(
			List<Component> components
	) {
		if (
				this.server == null ||
						components == null ||
						components.isEmpty()
		) {
			return;
		}

		List<Component> messages =
				List.copyOf(components);

		this.server.execute(() -> {
			for (Component component : messages) {
				this.server
						.getPlayerList()
						.broadcastSystemMessage(
								component,
								false
						);
			}
		});
	}

	@Override
	public CompletableFuture<String> getOnlinePlayers() {
		MinecraftServer currentServer = this.server;
		if (currentServer == null) {
			return MessageForwarder.super.getOnlinePlayers();
		}
		CompletableFuture<String> result =
				new CompletableFuture<>();
		currentServer.execute(() -> {
			try {
				List<String> names = currentServer.getPlayerList().getPlayers()
						.stream().map(player -> player.getName().getString())
						.sorted(String.CASE_INSENSITIVE_ORDER).toList();
				String heading = "Online players: " + names.size() + "/"
						+ currentServer.getPlayerList().getMaxPlayers();
				result.complete(heading + "\n" + (names.isEmpty()
						? "Nobody is online right now." : String.join(", ", names)));
			} catch (Exception exception) {
				result.completeExceptionally(exception);
			}
		});
		return result;
	}
}
