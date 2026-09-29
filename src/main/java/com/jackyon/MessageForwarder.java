package com.jackyon;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public interface MessageForwarder {

    /*
     * Sends a group of components to Minecraft in the same order.
     */
    void forwardComponentsToMinecraft(List<Component> components);

    /*
     * Keeps your existing String forwarding code working.
     */
    default void forwardToMinecraft(String message) {
        forwardComponentsToMinecraft(
                List.of(Component.literal(message))
        );
    }

    /*
     * Converts an image into colored chat lines.
     */
    default void forwardImageToMinecraft(
            String discordUsername,
            BufferedImage image
    ) {
        List<Component> messages = new ArrayList<>();

        MutableComponent header = Component
                .literal("[Discord] ")
                .withStyle(ChatFormatting.AQUA)
                .append(
                        Component.literal(discordUsername)
                                .withStyle(ChatFormatting.WHITE)
                )
                .append(
                        Component.literal(" sent an image:")
                                .withStyle(ChatFormatting.AQUA)
                );

        messages.add(header);
        messages.addAll(ChatImageRenderer.render(image));

        forwardComponentsToMinecraft(messages);
    }
}