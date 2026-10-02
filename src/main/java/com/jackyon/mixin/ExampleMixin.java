package com.jackyon.mixin;

import com.jackyon.Discordbot;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(PlayerAdvancements.class)
public abstract class ExampleMixin {

	@Unique
	private static final Logger DISCORDBRIDGE_LOGGER =
			LoggerFactory.getLogger("DiscordBridge-Advancements");

	@Unique
	private static final String DISCORDBRIDGE_AWARD_METHOD =
			"award(Lnet/minecraft/advancements/AdvancementHolder;" +
					"Ljava/lang/String;)Z";

	@Shadow
	private ServerPlayer player;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(
			AdvancementHolder advancement
	);

	/*
	 * A stack is used because datapack reward functions may award
	 * another advancement while the first award is still processing.
	 */
	@Unique
	private Deque<Boolean> discordbridge$previousCompletionStates;

	@Inject(
			method = DISCORDBRIDGE_AWARD_METHOD,
			at = @At("HEAD"),
			require = 1
	)
	private void discordbridge$beforeAward(
			AdvancementHolder advancement,
			String criterionName,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (discordbridge$previousCompletionStates == null) {
			discordbridge$previousCompletionStates =
					new ArrayDeque<>();
		}

		boolean wasAlreadyCompleted =
				getOrStartProgress(advancement).isDone();

		discordbridge$previousCompletionStates.push(
				wasAlreadyCompleted
		);
	}

	@Inject(
			method = DISCORDBRIDGE_AWARD_METHOD,
			at = @At("RETURN"),
			require = 1
	)
	private void discordbridge$afterAward(
			AdvancementHolder advancement,
			String criterionName,
			CallbackInfoReturnable<Boolean> cir
	) {
		boolean wasAlreadyCompleted = false;

		if (
				discordbridge$previousCompletionStates != null &&
						!discordbridge$previousCompletionStates.isEmpty()
		) {
			wasAlreadyCompleted =
					discordbridge$previousCompletionStates.pop();
		}

		String advancementId =
				advancement.id().toString();

		/*
		 * award() returns false when the criterion was not newly awarded.
		 */
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}

		AdvancementProgress progress =
				getOrStartProgress(advancement);

		/*
		 * Do not announce until the entire advancement is complete.
		 */
		if (!progress.isDone()) {
			return;
		}

		/*
		 * Prevent duplicate announcements when another optional
		 * criterion is awarded after completion.
		 */
		if (wasAlreadyCompleted) {
			return;
		}

		/*
		 * Ignore datapack helper advancements inside technical folders.
		 */
		if (discordbridge$isTechnicalAdvancement(advancementId)) {
			DISCORDBRIDGE_LOGGER.debug(
					"Ignoring technical advancement: {}",
					advancementId
			);

			return;
		}

		DisplayInfo display = advancement
				.value()
				.display()
				.orElse(null);

		/*
		 * Ignore displayless advancements.
		 *
		 * These are usually internal datapack triggers, inventory checks,
		 * recipe checks, or other advancements automatically granted
		 * when a player joins.
		 */
		if (display == null) {
			DISCORDBRIDGE_LOGGER.debug(
					"Ignoring displayless/internal advancement: {}",
					advancementId
			);

			return;
		}

		Discordbot bot =
				Discordbot.getInstance();

		if (bot == null) {
			DISCORDBRIDGE_LOGGER.error(
					"Discordbot instance is null while sending advancement {}",
					advancementId
			);

			return;
		}

		String playerName =
				player.getName().getString();

		String playerUuid =
				player.getUUID().toString();

		String title =
				display.title().getString();


		String description =
				display.description().getString();

		String advancementType =
				display.type().getSerializedName();

		try {
			switch (advancementType) {
				case "task" -> bot.sendEmbedTaskDiscord(
						playerName +
								" made the advancement: " +
								title,
						description,
						playerUuid
				);

				case "goal" -> bot.sendEmbedCompletedDiscord(
						playerName +
								" reached the goal: " +
								title,
						description,
						playerUuid
				);

				case "challenge" -> bot.sendEmbedCompletedDiscord(
						playerName +
								" completed the challenge: " +
								title,
						description,
						playerUuid
				);

				default -> bot.sendEmbedCompletedDiscord(
						playerName +
								" completed: " +
								title,
						description,
						playerUuid
				);
			}

			DISCORDBRIDGE_LOGGER.info(
					"Sent advancement to Discord: {} completed {}",
					playerName,
					advancementId
			);
		} catch (Exception exception) {
			DISCORDBRIDGE_LOGGER.error(
					"Failed to send advancement to Discord: {}",
					advancementId,
					exception
			);
		}
	}

	@Unique
	private static boolean discordbridge$isTechnicalAdvancement(
			String advancementId
	) {
		int namespaceSeparator =
				advancementId.indexOf(':');

		String advancementPath;

		if (namespaceSeparator >= 0) {
			advancementPath = advancementId.substring(
					namespaceSeparator + 1
			);
		} else {
			advancementPath = advancementId;
		}

		return advancementPath.startsWith("technical/") ||
				advancementPath.contains("/technical/");
	}
}
