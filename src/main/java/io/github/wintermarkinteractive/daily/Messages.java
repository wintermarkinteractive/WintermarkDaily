package io.github.wintermarkinteractive.daily;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Reads MiniMessage texts from the "messages" section of config.yml. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DailyPlugin plugin;
    private String prefix = "";

    public Messages(DailyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        prefix = raw("prefix");
    }

    /** Raw text from messages.<key>, or "" when missing. Uses the jar defaults as fallback. */
    public String raw(String key) {
        String value = plugin.getConfig().getString("messages." + key);
        return value == null ? "" : value;
    }

    /** Sends messages.<key> with the prefix. Empty messages are not sent (lets owners turn them off). */
    public void send(CommandSender to, String key, TagResolver... placeholders) {
        String text = raw(key);
        if (!text.isEmpty()) {
            to.sendMessage(parse(prefix + text, placeholders));
        }
    }

    /** Sends any MiniMessage text with the prefix. */
    public void sendRaw(CommandSender to, String miniMessage, TagResolver... placeholders) {
        if (miniMessage != null && !miniMessage.isEmpty()) {
            to.sendMessage(parse(prefix + miniMessage, placeholders));
        }
    }

    public Component parse(String miniMessage, TagResolver... placeholders) {
        return MM.deserialize(miniMessage, TagResolver.resolver(placeholders));
    }
}
