package com.jackyon;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class Discordbridge implements ModInitializer, MessageForwarder {

	public static final Logger LOGGER =
			LoggerFactory.getLogger("DiscordMaybe");

	private Discordbot bot;
	private MinecraftServer server;
	private static Discordbridge INSTANCE;
	private static boolean noChatReportEnabled;
	public static BotConfig config;


	@Override
	public void onInitialize() {

		INSTANCE = this;
		config = BotConfig.load();

		noChatReportEnabled = config.noChatReport ==1;

		final String token = config.token;
		final long channelId = config.channelId;

		// =========================================================
		// SERVER START / STOP
		// =========================================================

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			this.server = server;

			try {
				bot = new Discordbot(token, channelId, this);
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
		// SYSTEM MESSAGES
		//
		// Handles:
		//   - Advancements
		//   - Death messages
		// =========================================================
/*
		ServerMessageEvents.GAME_MESSAGE.register(
				(server, text, overlay) -> {

					if (!(text.getContents()
							instanceof TranslatableContents translatable)) {

						return;
					}

					String key = translatable.getKey();


					// =================================================
					// ADVANCEMENTS
					//
					// chat.type.advancement.task
					// chat.type.advancement.goal
					// chat.type.advancement.challenge
					// =================================================

					if (key.startsWith("chat.type.advancement.")) {

						Component playerArgument = argAsComponent(
								translatable.getArgs(),
								0
						);

						Component titleArgument = argAsComponent(
								translatable.getArgs(),
								1
						);

						ServerPlayer player =
								resolvePlayerFromMessageArg(
										server,
										playerArgument
								);

						String cleanName =
								player != null
										? player.getName().getString()
										: playerArgument.getString();

						String uuid =
								player != null
										? player.getUUID().toString()
										: null;

						String advancementTitle =
								titleArgument.getString();

						if (bot != null) {

							if (key.endsWith(".task")) {

								bot.sendEmbedTaskDiscord(
										cleanName
												+ " made the advancement: "
												+ advancementTitle,
										uuid
								);

							} else if (
									key.endsWith(".goal")
											|| key.endsWith(".challenge")
							) {

								bot.sendEmbedCompletedDiscord(
										cleanName
												+ " completed: "
												+ advancementTitle,
										uuid
								);

							} else {

								bot.sendEmbedTaskDiscord(
										cleanName
												+ " got: "
												+ advancementTitle,
										uuid
								);
							}
						}

						return;
					}

/*
					// =================================================
					// DEATH MESSAGES
					//
					// Handles all vanilla death.* translations.
					// =================================================

					if (key.startsWith("death.")) {

						// Exact final text shown by Minecraft.
						String fullMessage = text.getString();

						Component victimArgument = argAsComponent(
								translatable.getArgs(),
								0
						);

						ServerPlayer victim =
								resolvePlayerFromMessageArg(
										server,
										victimArgument
								);

						String uuid =
								victim != null
										? victim.getUUID().toString()
										: null;

						if (bot != null) {
							bot.sendEmbedDeathDiscord(
									fullMessage,
									uuid
							);
						}
					}
				}
		);
*/

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
	 * Safely converts a translation argument into a Component.
	 */
	private static Component argAsComponent(
			Object[] arguments,
			int index
	) {

		if (arguments == null
				|| index < 0
				|| index >= arguments.length) {

			return Component.empty();
		}

		Object argument = arguments[index];

		if (argument instanceof Component component) {
			return component;
		}

		return Component.literal(String.valueOf(argument));
	}


	/**
	 * Attempts to find the real ServerPlayer represented by
	 * a Component used in an advancement or death message.
	 *
	 * This works with:
	 *   - normal usernames
	 *   - display names
	 *   - scoreboard/team prefixes and suffixes
	 */
	private static ServerPlayer resolvePlayerFromMessageArg(
			MinecraftServer server,
			Component playerArgument
	) {

		String messageName = playerArgument.getString();


		// First: compare the fully decorated display name.
		for (ServerPlayer player :
				server.getPlayerList().getPlayers()) {

			Component displayName = player.getDisplayName();

			if (displayName != null
					&& messageName.equals(displayName.getString())) {

				return player;
			}
		}


		// Second: compare raw usernames.
		for (ServerPlayer player :
				server.getPlayerList().getPlayers()) {

			String rawName =
					player.getName().getString();

			if (messageName.equals(rawName)) {
				return player;
			}
		}


		// Third: fallback for decorated names such as:
		//
		// [Admin] Jack
		// ★ Jack
		// Jack [Team]
		//
		ServerPlayer bestMatch = null;
		int longestName = -1;

		for (ServerPlayer player :
				server.getPlayerList().getPlayers()) {

			String rawName =
					player.getName().getString();

			if ((messageName.contains(rawName)
					|| messageName.endsWith(rawName))
					&& rawName.length() > longestName) {

				bestMatch = player;
				longestName = rawName.length();
			}
		}

		return bestMatch;
	}


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


	// =============================================================
	// DISCORD -> MINECRAFT
	// =============================================================
/*
	@Override
	public void forwardToMinecraft(String message) {

		MinecraftServer currentServer = this.server;

		if (currentServer == null
				|| message == null
				|| message.isBlank()) {

			return;
		}

		/*
		 * Discord callbacks normally happen on the Discord bot's
		 * thread, not Minecraft's server thread.
		 *
		 * Schedule the Minecraft work safely on the server thread.
		 */
	/*
		currentServer.execute(() -> {

			Component minecraftMessage =
					Component.literal(message);

			for (ServerPlayer player :
					currentServer.getPlayerList().getPlayers()) {

				player.sendSystemMessage(minecraftMessage);
			}
		});
	}
	*/
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
}