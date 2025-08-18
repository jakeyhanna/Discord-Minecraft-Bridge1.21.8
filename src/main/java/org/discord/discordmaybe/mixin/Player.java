package org.discord.discordmaybe.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.discord.discordmaybe.Discordbot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class Player{
    @Inject(
            method = "grantCriterion",
            at = @At("TAIL")
    )
    private void onAdvancementGranted(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir){
        if (cir.getReturnValue() == true){
            ServerPlayerEntity player = ((PlayerAdvancementTrackerAccessor) this).getOwner();

            // Trigger your custom event here
            System.out.println(player.getName().getString() + " earned advancement: " + advancement.id());
            //Discordbot bot = null;
            //bot.sendEmbedTaskDiscord(advancement.toString(),player.getUuid().toString(), player.getName().toString());
        }
    }
}