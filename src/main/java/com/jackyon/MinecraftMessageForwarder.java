package com.jackyon;

import java.util.List;
import java.util.Objects;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MinecraftMessageForwarder
        implements MessageForwarder {

    private final MinecraftServer server;

    public MinecraftMessageForwarder(
            MinecraftServer server
    ) {
        this.server = Objects.requireNonNull(
                server,
                "Minecraft server cannot be null"
        );
    }

    @Override
    public void forwardComponentsToMinecraft(
            List<Component> components
    ) {
        if (components == null || components.isEmpty()) {
            return;
        }

        /*
         * Copy the list before scheduling it onto the server thread.
         */
        List<Component> safeComponents =
                List.copyOf(components);

        server.execute(() -> {
            for (Component component : safeComponents) {
                for (
                        ServerPlayer player :
                        server.getPlayerList().getPlayers()
                ) {
                    player.sendSystemMessage(component);
                }
            }
        });
    }
}
