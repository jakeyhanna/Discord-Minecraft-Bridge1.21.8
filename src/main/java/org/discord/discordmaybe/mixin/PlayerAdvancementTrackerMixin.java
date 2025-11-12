package org.discord.discordmaybe.mixin;

import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.discord.discordmaybe.DiscordMaybe;
import org.discord.discordmaybe.Discordbot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires right after a criterion is granted. If that grant finishes the advancement,
 * we forward it to Discord (datapack or vanilla, chat on or off).
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {

    @Shadow @Final private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void dm$afterGrant(AdvancementEntry entry, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        // only act when something actually changed
        if (!cir.getReturnValueZ()) return;

        // completed yet?
        var progress = ((PlayerAdvancementTracker)(Object)this).getProgress(entry);
        if (!progress.isDone()) return;

        // visible and has a display? (skip hidden/root-without-display)
        AdvancementDisplay display = entry.value().display().orElse(null);
        if (display == null) return;

        Discordbot bot = DiscordMaybe.getBot();
        if (bot == null) return;

        String playerName = owner.getGameProfile().name();
        String uuid = owner.getUuidAsString();
        Text title = display.getTitle();
        Text desc = display.getDescription();

        // frame: "task", "goal", "challenge"
        String frame = display.getFrame().asString();

        if ("task".equals(frame)) {
            bot.sendEmbedTaskDiscord(playerName + " made the advancement: " + title.getString(), uuid, desc.getString());
        } else {
            bot.sendEmbedCompletedDiscord(playerName + " completed: " + title.getString(), uuid, desc.getString());
        }
    }
}
