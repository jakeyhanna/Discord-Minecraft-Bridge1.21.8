package com.jackyon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatImageRenderer {

    /*
     * Increase MAX_WIDTH for more image detail.
     * Increase MAX_HEIGHT to allow taller portrait images while preserving
     * their proportions. Square and landscape images remain width-limited.
     *
     * Larger images will occupy more chat space and create
     * significantly larger chat packets.
     */
    private static final int MAX_WIDTH = 28;
    private static final int MAX_HEIGHT = 24;

    /*
     * Minecraft characters are taller than they are wide.
     * This correction prevents images from appearing stretched vertically.
     */
    private static final double CHARACTER_ASPECT_CORRECTION = 0.55D;

    /*
     * Transparent pixels are blended against approximately the same
     * dark background used by Minecraft chat.
     */
    private static final int BACKGROUND_RED = 16;
    private static final int BACKGROUND_GREEN = 16;
    private static final int BACKGROUND_BLUE = 16;

    private static final int TRANSPARENT_COLOR = -1;
    private static final int MINIMUM_ALPHA = 32;

    private ChatImageRenderer() {
    }

    public static List<Component> render(BufferedImage original) {
        if (original == null) {
            return List.of(
                    Component.literal("[Image could not be rendered]")
            );
        }

        int[] dimensions = calculateDimensions(
                original.getWidth(),
                original.getHeight()
        );

        int targetWidth = dimensions[0];
        int targetHeight = dimensions[1];

        BufferedImage scaled = resize(
                original,
                targetWidth,
                targetHeight
        );

        List<Component> lines = new ArrayList<>();

        for (int y = 0; y < scaled.getHeight(); y++) {
            MutableComponent line = Component.empty();

            int currentColor = Integer.MIN_VALUE;
            StringBuilder currentRun = new StringBuilder();

            for (int x = 0; x < scaled.getWidth(); x++) {
                int pixelColor = getChatColor(
                        scaled.getRGB(x, y)
                );

                if (pixelColor != currentColor) {
                    appendRun(
                            line,
                            currentRun,
                            currentColor
                    );

                    currentColor = pixelColor;
                }

                if (pixelColor == TRANSPARENT_COLOR) {
                    currentRun.append(' ');
                } else {
                    currentRun.append('█');
                }
            }

            appendRun(
                    line,
                    currentRun,
                    currentColor
            );

            lines.add(line);
        }

        return lines;
    }

    private static int[] calculateDimensions(
            int originalWidth,
            int originalHeight
    ) {
        if (originalWidth <= 0 || originalHeight <= 0) {
            return new int[]{1, 1};
        }

        int width = Math.min(
                originalWidth,
                MAX_WIDTH
        );

        double correctedRatio =
                originalHeight /
                        (double) originalWidth *
                        CHARACTER_ASPECT_CORRECTION;

        int height = Math.max(
                1,
                (int) Math.round(width * correctedRatio)
        );

        /*
         * If the calculated image is too tall, reduce its width too,
         * preserving the corrected aspect ratio.
         */
        if (height > MAX_HEIGHT) {
            height = MAX_HEIGHT;

            width = Math.max(
                    1,
                    (int) Math.round(
                            height / correctedRatio
                    )
            );

            width = Math.min(width, MAX_WIDTH);
        }

        return new int[]{width, height};
    }

    private static BufferedImage resize(
            BufferedImage original,
            int width,
            int height
    ) {
        BufferedImage resized = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = resized.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_COLOR_RENDERING,
                    RenderingHints.VALUE_COLOR_RENDER_QUALITY
            );

            graphics.drawImage(
                    original,
                    0,
                    0,
                    width,
                    height,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return resized;
    }

    private static int getChatColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;

        if (alpha < MINIMUM_ALPHA) {
            return TRANSPARENT_COLOR;
        }

        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        /*
         * Blend partially transparent pixels against the chat background.
         */
        red = blendChannel(
                red,
                BACKGROUND_RED,
                alpha
        );

        green = blendChannel(
                green,
                BACKGROUND_GREEN,
                alpha
        );

        blue = blendChannel(
                blue,
                BACKGROUND_BLUE,
                alpha
        );

        /*
         * Slight color quantization reduces the number of separate
         * styled components without heavily damaging image quality.
         */
        red = quantizeChannel(red);
        green = quantizeChannel(green);
        blue = quantizeChannel(blue);

        return (red << 16) |
                (green << 8) |
                blue;
    }

    private static int blendChannel(
            int foreground,
            int background,
            int alpha
    ) {
        return (
                foreground * alpha +
                        background * (255 - alpha)
        ) / 255;
    }

    private static int quantizeChannel(int value) {
        /*
         * Converts each channel to one of 16 possible levels.
         */
        return Math.min(
                255,
                Math.max(0, Math.round(value / 17.0F) * 17)
        );
    }

    private static void appendRun(
            MutableComponent line,
            StringBuilder run,
            int rgb
    ) {
        if (run.isEmpty()) {
            return;
        }

        MutableComponent part = Component.literal(
                run.toString()
        );

        if (rgb != TRANSPARENT_COLOR &&
                rgb != Integer.MIN_VALUE) {
            part.withStyle(
                    style -> style.withColor(rgb)
            );
        }

        line.append(part);
        run.setLength(0);
    }
}
