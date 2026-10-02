package com.jackyon;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

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
    /*
     * Matches @Username at the start of a line or after whitespace.
     * Discord usernames are 2-32 chars of letters, digits, dots,
     * underscores, and hyphens.
     */
    private static final Pattern DISCORD_MENTION_PATTERN =
            Pattern.compile(
                    "(?<=^|\\s)@([A-Za-z0-9_.\\-]{2,32})"
            );

    private static Discordbot INSTANCE;

    private final String token;
    private final long channelId;
    private final MessageForwarder messageForwarder;

    private ShardManager shardManager;
    private final BotConfig config;

    public Discordbot(
            String token,
            long channelId,
            MessageForwarder messageForwarder
    ) {
        this(token, channelId, messageForwarder, new BotConfig());
    }

    public Discordbot(String token, long channelId,
                      MessageForwarder messageForwarder, BotConfig config) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
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
        builder.enableIntents(
                GatewayIntent.GUILD_MEMBERS
        );
        builder.setMemberCachePolicy(
                MemberCachePolicy.ALL
        );

        builder.addEventListeners(this);

        this.shardManager = builder.build();

        LOGGER.info(
                "Discord bot started for channel {}",
                this.channelId
        );
    }

    @Override
    public void onReady(ReadyEvent event) {
        TextChannel channel = event.getJDA().getTextChannelById(channelId);
        if (channel == null) {
            LOGGER.warn("Cannot register /players: configured Discord text channel was not found");
            return;
        }
        channel.getGuild().upsertCommand("players", "Show who is online on the Minecraft server")
                .queue(command -> LOGGER.info("Registered Discord /players command"),
                        exception -> LOGGER.error("Failed to register /players", exception));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("players")) {
            return;
        }
        if (event.getChannel().getIdLong() != channelId) {
            event.reply("Use /players in the configured Minecraft bridge channel.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue(hook -> {
            messageForwarder.getOnlinePlayers().orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((players, exception) -> {
                        String reply = players;
                        if (exception != null) {
                            LOGGER.warn("Failed to query Minecraft players", exception);
                            reply = "Minecraft server is unavailable. Try again shortly.";
                        }
                        if (reply.length() > 2000) {
                            reply = reply.substring(0, 1997) + "...";
                        }
                        hook.editOriginal(new MessageEditBuilder().setContent(reply)
                                .setAllowedMentions(List.of()).build()).queue(
                                success -> {},
                                failure -> LOGGER.warn("Failed to reply to /players", failure));
                    });
        }, exception -> LOGGER.warn("Failed to acknowledge /players", exception));
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

        String resolved = resolveDiscordMentions(
                message,
                channel.getGuild()
        );

        MessageCreateBuilder builder =
                new MessageCreateBuilder();

        builder.setContent(resolved);
        builder.setAllowedMentions(
                EnumSet.of(Message.MentionType.USER)
        );

        channel.sendMessage(
                builder.build()
        ).queue(
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

        String greeting = config.getJoinGreeting(id, message);
        if (!greeting.isBlank()) {
            embed.setDescription(greeting.length() > 4096
                    ? greeting.substring(0, 4096) : greeting);
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

    /*
     * Replaces @Username patterns in the message with Discord
     * mention markup (<@memberId>) so that pinging a Discord
     * user from Minecraft chat is as simple as typing their name.
     *
     * Matches exact names first, then unique prefixes or close spelling.
     * Unresolved or ambiguous names are left untouched.
     */
    private String resolveDiscordMentions(
            String message,
            Guild guild
    ) {
        if (message == null || message.isBlank()) {
            return message;
        }

        if (guild == null) {
            return message;
        }

        Matcher matcher =
                DISCORD_MENTION_PATTERN.matcher(message);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String userName = matcher.group(1);

            Member member = NameMatcher.findClosest(userName, guild.getMembers(),
                    candidate -> List.of(candidate.getEffectiveName(), candidate.getUser().getName()));

            String replacement = member == null
                    ? matcher.group()
                    : "<@" + member.getId() + ">";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
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
