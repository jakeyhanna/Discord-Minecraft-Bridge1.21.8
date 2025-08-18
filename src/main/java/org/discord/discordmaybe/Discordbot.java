package org.discord.discordmaybe;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.minecraft.text.Text;
import javax.security.auth.login.LoginException;
import java.awt.*;


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
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token);
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.playing("Minecraft")).enableIntents(GatewayIntent.MESSAGE_CONTENT);
        builder.addEventListeners(this);
        this.shardManager = builder.build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages from bots and other channels
        if (event.getAuthor().isBot() || event.getChannel().getIdLong() != channelId) {
            return;
        }
        // Forward Discord message to Minecraft
        //String message = String.format("[Discord] %s: %s",
        //        event.getAuthor().getName(),
        //        event.getMessage().getContentDisplay());
        String message = event.getAuthor().getName() + ": " + event.getMessage().getContentDisplay();
        messageForwarder.forwardToMinecraft(message);
    }

    public void sendToDiscord(String message) {
        if (shardManager == null) return;

        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }


    public void sendEmbedMessageDiscord(String message){
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle(message);

        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void sendEmbedjoinDiscord(String message, String id) {
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("**" + message + " Joined!**");
                embed.setDescription("Yep Yep horray");
                embed.setThumbnail("https://api.mineatar.io/face/"+id+"?scale=12");
                embed.setColor(Color.GREEN);


        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void sendEmbedTaskDiscord(String message, String id) {
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
        //embed.setImage("https://api.mineatar.io/face/"+id+"?scale=12");
        //embed.setDescription(pname + "got the task" + message);
        embed.setAuthor(message,null,"https://minotar.net/helm/"+id+"/512.png");
        embed.setColor(Color.GREEN);


        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void sendEmbedCompletedDiscord(String message, String id) {
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
        //embed.setImage("https://api.mineatar.io/face/"+id+"?scale=12");
        //embed.setDescription(pname + "got the task" + message);
        embed.setAuthor(message,null,"https://minotar.net/helm/"+id+"/512.png");
        embed.setColor(Color.MAGENTA);


        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void sendEmbedDeathDiscord(String message, String id) {
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
        //embed.setImage("https://api.mineatar.io/face/"+id+"?scale=12");

        //embed.setDescription(message);
        embed.setAuthor(message,null,"https://minotar.net/helm/"+id+"/512.png");
        embed.setColor(Color.RED);


        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void sendEmbedleaveDiscord(String message, String id) {
        if (shardManager == null) return;
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("**" + message + " left!**");
        embed.setDescription("We hope to see you soon!");
        embed.setThumbnail("https://api.mineatar.io/face/"+id+"?scale=12");
        embed.setColor(Color.RED);


        TextChannel channel = shardManager.getTextChannelById(channelId);
        if (channel != null){
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void stop() {
        if (shardManager != null) {
            shardManager.shutdown();
        }
    }
}