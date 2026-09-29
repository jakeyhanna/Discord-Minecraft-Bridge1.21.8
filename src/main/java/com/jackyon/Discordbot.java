package com.jackyon;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Discordbot extends ListenerAdapter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("DiscordBridge");

    /*
     * Maximum compressed Discord attachment size: 5 MiB.
     */
    private static final long MAX_IMAGE_BYTES =
            5L * 1024L * 1024L;

    /*
     * Protects the server against unusually large decoded images.
     */
    private static final int MAX_IMAGE_SIDE = 8192;

    private static final long MAX_IMAGE_PIXELS =
            25_000_000L;

    private static Discordbot INSTANCE;

    private final String token;
    private final long channelId;
    private final MessageForwarder messageForwarder;

    private ShardManager shardManager;

    public Discordbot(
            String token,
            long channelId,
            MessageForwarder messageForwarder
    ) {
        this.token = Objects.requireNonNull(
                token,
                "Discord token cannot be null"
        );

        this.channelId = channelId;

        this.messageForwarder = Objects.requireNonNull(
                messageForwarder,
                "MessageForwarder cannot be null"
        );

        /*
         * Required by the advancement Mixin.
         */
        INSTANCE = this;
    }

    public static Discordbot getInstance() {
        return INSTANCE;
    }

    public void start() throws LoginException {
        if (this.shardManager != null) {
            LOGGER.warn("Discord bot is already running");
            return;
        }

        DefaultShardManagerBuilder builder =
                DefaultShardManagerBuilder.createDefault(
                        this.token
                );

        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(
                Activity.playing("Minecraft")
        );

        /*
         * Required for message text and attachments.
         */
        builder.enableIntents(
                GatewayIntent.MESSAGE_CONTENT
        );

        builder.addEventListeners(this);

        this.shardManager = builder.build();

        LOGGER.info(
                "Discord bot started for channel {}",
                this.channelId
        );
    }

    @Override
    public void onMessageReceived(
            MessageReceivedEvent event
    ) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (
                event.getChannel().getIdLong() !=
                        this.channelId
        ) {
            return;
        }

        String authorName;

        if (event.getMember() != null) {
            authorName =
                    event.getMember().getEffectiveName();
        } else {
            authorName =
                    event.getAuthor().getName();
        }

        String content = event
                .getMessage()
                .getContentDisplay();

        /*
         * Forward ordinary Discord text.
         */
        if (content != null && !content.isBlank()) {
            this.messageForwarder.forwardToMinecraft(
                    "[Discord] " +
                            authorName +
                            ": " +
                            content
            );
        }

        /*
         * Render Discord image attachments in Minecraft chat.
         */
        for (
                Message.Attachment attachment :
                event.getMessage().getAttachments()
        ) {
            handleImageAttachment(
                    authorName,
                    attachment
            );
        }
    }

    private void handleImageAttachment(
            String authorName,
            Message.Attachment attachment
    ) {
        /*
         * Ignore PDFs, videos, ZIP files and other non-image attachments.
         */
        if (!attachment.isImage()) {
            return;
        }

        if (attachment.getSize() > MAX_IMAGE_BYTES) {
            this.messageForwarder.forwardToMinecraft(
                    "[Discord] " +
                            authorName +
                            " sent an image that was too large"
            );

            LOGGER.warn(
                    "Rejected Discord image {} because it was {} bytes",
                    attachment.getFileName(),
                    attachment.getSize()
            );

            return;
        }

        int reportedWidth = attachment.getWidth();
        int reportedHeight = attachment.getHeight();

        if (
                reportedWidth > 0 &&
                        reportedHeight > 0 &&
                        (
                                reportedWidth > MAX_IMAGE_SIDE ||
                                        reportedHeight > MAX_IMAGE_SIDE ||
                                        (long) reportedWidth *
                                                reportedHeight >
                                                MAX_IMAGE_PIXELS
                        )
        ) {
            this.messageForwarder.forwardToMinecraft(
                    "[Discord] " +
                            authorName +
                            " sent an image with dimensions that were too large"
            );

            LOGGER.warn(
                    "Rejected Discord image {} with dimensions {}x{}",
                    attachment.getFileName(),
                    reportedWidth,
                    reportedHeight
            );

            return;
        }

        /*
         * JDA performs this download asynchronously.
         */
        attachment.getProxy()
                .download()
                .thenAccept(inputStream -> {
                    readAndForwardImage(
                            authorName,
                            attachment,
                            inputStream
                    );
                })
                .exceptionally(exception -> {
                    LOGGER.error(
                            "Failed to download Discord image {}",
                            attachment.getFileName(),
                            exception
                    );

                    this.messageForwarder
                            .forwardToMinecraft(
                                    "[Discord] Failed to download " +
                                            authorName +
                                            "'s image"
                            );

                    return null;
                });
    }

    private void readAndForwardImage(
            String authorName,
            Message.Attachment attachment,
            InputStream inputStream
    ) {
        /*
         * JDA requires the downloaded InputStream to be closed manually.
         */
        try (InputStream input = inputStream) {
            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                LOGGER.warn(
                        "ImageIO could not decode Discord attachment {}",
                        attachment.getFileName()
                );

                this.messageForwarder
                        .forwardToMinecraft(
                                "[Discord] Could not decode " +
                                        authorName +
                                        "'s image"
                        );

                return;
            }

            long decodedPixels =
                    (long) image.getWidth() *
                            image.getHeight();

            if (
                    image.getWidth() > MAX_IMAGE_SIDE ||
                            image.getHeight() > MAX_IMAGE_SIDE ||
                            decodedPixels > MAX_IMAGE_PIXELS
            ) {
                LOGGER.warn(
                        "Rejected decoded Discord image {} with dimensions {}x{}",
                        attachment.getFileName(),
                        image.getWidth(),
                        image.getHeight()
                );

                this.messageForwarder
                        .forwardToMinecraft(
                                "[Discord] " +
                                        authorName +
                                        "'s image was too large to render"
                        );

                return;
            }

            this.messageForwarder
                    .forwardImageToMinecraft(
                            authorName,
                            image
                    );

            LOGGER.info(
                    "Rendered Discord image {} from {} in Minecraft chat",
                    attachment.getFileName(),
                    authorName
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to read Discord image {}",
                    attachment.getFileName(),
                    exception
            );

            this.messageForwarder.forwardToMinecraft(
                    "[Discord] Failed to render " +
                            authorName +
                            "'s image"
            );
        }
    }

    public void sendToDiscord(String message) {
        TextChannel channel = getTextChannel();

        if (channel == null) {
            return;
        }

        channel.sendMessage(message).queue(
                success -> {
                },
                exception -> LOGGER.error(
                        "Failed to send Discord message",
                        exception
                )
        );
    }

    public void sendEmbedMessageDiscord(
            String message
    ) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(message);

        sendEmbed(
                embed,
                "generic embed"
        );
    }

    public void sendEmbedjoinDiscord(
            String message,
            String id
    ) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(
                "**" + message + " Joined!**"
        );

        switch (id) {
            case "0ce55a56-1225-4645-b3d9-ea1d8fc1c694" ->
                    embed.setDescription(
                            "ALSALAM ALIKUM ALIKUM ALSALAM"
                    );

            case "00f0b3c8-4120-4c58-a416-44f342e90fb0" ->
                    embed.setDescription(
                            "ZA DOM SPREMNI"
                    );

            case "746557f3-c24b-4058-9b9a-8e50eeefb1d2" ->
                    embed.setDescription(
                            "I love you Nati"
                    );

            default ->
                    embed.setDescription(
                            "YEP YEP HORRAY!!!"
                    );
        }

        embed.setThumbnail(
                "https://api.mineatar.io/face/" +
                        id +
                        "?scale=12"
        );

        embed.setColor(Color.GREEN);

        sendEmbed(
                embed,
                "join embed"
        );
    }

    /*
     * Keeps older two-argument calls working.
     */
    public void sendEmbedTaskDiscord(
            String message,
            String id
    ) {
        sendEmbedTaskDiscord(
                message,
                null,
                id
        );
    }

    public void sendEmbedTaskDiscord(
            String message,
            String description,
            String id
    ) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setAuthor(
                message,
                null,
                "https://minotar.net/helm/" +
                        id +
                        "/512.png"
        );

        if (
                description != null &&
                        !description.isBlank()
        ) {
            embed.setDescription(description);
        }

        embed.setColor(Color.GREEN);

        sendEmbed(
                embed,
                "task advancement embed"
        );
    }

    /*
     * Keeps older two-argument calls working.
     */
    public void sendEmbedCompletedDiscord(
            String message,
            String id
    ) {
        sendEmbedCompletedDiscord(
                message,
                null,
                id
        );
    }

    public void sendEmbedCompletedDiscord(
            String message,
            String description,
            String id
    ) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setAuthor(
                message,
                null,
                "https://minotar.net/helm/" +
                        id +
                        "/512.png"
        );

        if (
                description != null &&
                        !description.isBlank()
        ) {
            embed.setDescription(description);
        }

        embed.setColor(Color.MAGENTA);

        sendEmbed(
                embed,
                "completed advancement embed"
        );
    }

    public void sendEmbedDeathDiscord(
            String message,
            String id
    ) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setAuthor(
                message,
                null,
                "https://minotar.net/helm/" +
                        id +
                        "/512.png"
        );

        embed.setColor(Color.RED);

        sendEmbed(
                embed,
                "death embed"
        );
    }

    public void sendEmbedStartserver() {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(
                "**:white_check_mark: SERVER HAS BEEN STARTED**"
        );

        embed.setColor(Color.GREEN);

        sendEmbed(
                embed,
                "server-start embed"
        );
    }

    public void sendEmbedStopserver() {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(
                "**:x: SERVER HAS BEEN STOPPED**"
        );

        embed.setColor(Color.RED);

        sendEmbed(
                embed,
                "server-stop embed"
        );
    }

    public void sendEmbedleaveDiscord(
            String message,
            String id
    ) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(
                "**" + message + " left!**"
        );

        embed.setDescription(
                "We hope to see you soon!"
        );

        embed.setThumbnail(
                "https://api.mineatar.io/face/" +
                        id +
                        "?scale=12"
        );

        embed.setColor(Color.RED);

        sendEmbed(
                embed,
                "leave embed"
        );
    }

    private TextChannel getTextChannel() {
        if (this.shardManager == null) {
            LOGGER.error(
                    "Cannot send a Discord message because the bot is not started"
            );

            return null;
        }

        TextChannel channel =
                this.shardManager.getTextChannelById(
                        this.channelId
                );

        if (channel == null) {
            LOGGER.error(
                    "Discord channel {} was not found",
                    this.channelId
            );

            return null;
        }

        return channel;
    }

    private void sendEmbed(
            EmbedBuilder embed,
            String type
    ) {
        TextChannel channel = getTextChannel();

        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(
                embed.build()
        ).queue(
                success -> LOGGER.debug(
                        "Sent {}",
                        type
                ),
                exception -> LOGGER.error(
                        "Failed to send {}",
                        type,
                        exception
                )
        );
    }

    public void stop() {
        if (this.shardManager != null) {
            this.shardManager.shutdown();
            this.shardManager = null;
        }

        if (INSTANCE == this) {
            INSTANCE = null;
        }

        LOGGER.info("Discord bot stopped");
    }
}