# Discord Bridge

A Fabric mod that bridges Minecraft chat, player events, advancements, and images
with a Discord channel.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Build

Use JDK 25 for this Minecraft 26.3 project:

```sh
./gradlew build
```

On Windows, run `gradlew.bat build`. Built JARs are written to `build/libs/`;
use the JAR without the `-sources` suffix.

The first server start creates `config/discordbot-config.json`. Stop the server,
set the bot `token` and bridge `channelId`, then restart. Enable the Message
Content and Server Members privileged intents in the Discord Developer Portal.
Keep your bot token private.

## Ping Discord members from Minecraft

Type `@name` in Minecraft chat (or `/botsay`) to ping a Discord member without
looking up their ID. Names are matched without case sensitivity against Discord
nicknames and usernames. Exact matches take priority; otherwise the bot accepts
a unique prefix of at least three characters or a close spelling (one edit per
four typed characters, with a minimum of one and a maximum of three).

For example, `@jacky` can match `Jackyon`, and `@jackyn` can match `Jackyon`.
If several members are equally close, or no name is close enough, the original
text is kept. Put `@name` at the start of the message or after whitespace.

## Discord /players

The bot registers `/players` in the Discord server containing your configured
bridge channel when it connects. Use it in that channel to see the online player
count, server capacity, and player names. An empty server gets a friendly message.
The command is added without replacing other Discord commands.

Invite the bot with the `bot` and `applications.commands` scopes and allow members
to use application commands in the bridge channel. If the command does not appear,
check the bot logs for registration errors and the bot's invite scopes.

## Custom join greetings

On startup, older `config/discordbot-config.json` files are updated with
`defaultJoinGreeting` and `joinGreetings`, preserving existing settings and the
previous built-in greetings. Edit these fields while the server is stopped, then
restart it. Greetings appear in the Discord join embed.

Example fields to edit inside your existing configuration:

```json
{
  "defaultJoinGreeting": "Welcome, {player}!",
  "joinGreetings": {
    "0ce55a56-1225-4645-b3d9-ea1d8fc1c694": "Hey {player}, welcome back!"
  }
}
```

Keep your existing `token`, `channelId`, and `noChatReport` fields. Greeting keys
are player UUIDs; `{player}` is replaced with the Minecraft username. Use `\n`
inside a greeting for a new line. An empty greeting hides the embed description.
Remove a player's entry to use the default greeting, or use an empty map `{}` to
give everyone the default greeting.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
