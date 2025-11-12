package org.discord.discordmaybe;

import java.awt.Color;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.minecraft.text.Text;

public class Discordbot extends ListenerAdapter {
    private ShardManager shardManager;
    private final String token;
    private final long channelId;
    private MessageForwarder messageForwarder;

    public Discordbot(String token, long channelId, MessageForwarder messageForwarder) {
        this.token = token;
        this.channelId = channelId;
        this.messageForwarder = messageForwarder;
    }

    public void start() throws LoginException {
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(this.token);
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.playing("Minecraft")).enableIntents(GatewayIntent.MESSAGE_CONTENT, new GatewayIntent[0]);
        builder.addEventListeners(new Object[]{this});
        this.shardManager = builder.build();
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getChannel().getIdLong() == this.channelId) {
            String var10000 = event.getAuthor().getName();
            String message = var10000 + ": " + event.getMessage().getContentDisplay();
            this.messageForwarder.forwardToMinecraft(message);
        }
    }

    public void sendToDiscord(String message) {
        if (this.shardManager != null) {
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
            }

        }
    }

    public void sendEmbedMessageDiscord(String message) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(message);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedjoinDiscord(String message, String id) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("**" + message + " Joined!**");
            embed.setDescription("Yep Yep horray");
            embed.setThumbnail("https://api.mineatar.io/face/" + id + "?scale=12");
            embed.setColor(Color.GREEN);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedTaskDiscord(String message, String id, String Desc) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(message, (String)null, "https://minotar.net/helm/" + id + "/512.png");
            embed.setColor(Color.GREEN);
            embed.setDescription(Desc);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedCompletedDiscord(String message, String id,String desc) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(message, (String)null, "https://minotar.net/helm/" + id + "/512.png");
            embed.setColor(Color.MAGENTA);
            embed.setDescription(desc);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedDeathDiscord(String message, String id) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(message, (String)null, "https://minotar.net/helm/" + id + "/512.png");
            embed.setColor(Color.RED);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedStartserver() {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("**:white_check_mark:SERVER HAS BEEN STARTED**");
            embed.setColor(Color.GREEN);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedStopserver() {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("**:x:SERVER HAS BEEN STOPPED**");
            embed.setColor(Color.RED);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void sendEmbedleaveDiscord(String message, String id) {
        if (this.shardManager != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("**" + message + " left!**");
            embed.setDescription("We hope to see you soon!");
            embed.setThumbnail("https://api.mineatar.io/face/" + id + "?scale=12");
            embed.setColor(Color.RED);
            TextChannel channel = this.shardManager.getTextChannelById(this.channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }

        }
    }

    public void stop() {
        if (this.shardManager != null) {
            this.shardManager.shutdown();
        }

    }
}
 