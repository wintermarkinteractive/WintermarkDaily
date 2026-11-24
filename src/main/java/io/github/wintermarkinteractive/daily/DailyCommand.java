package io.github.wintermarkinteractive.daily;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /daily (alias /dailyrewards). */
public final class DailyCommand implements BasicCommand {

    private static final List<String> ADMIN_SUBS = List.of(
            "info", "freezes", "setstreak", "setday", "reset", "nextday", "missday", "timezone", "reload", "help");
    private static final List<String> PLAYER_ARG_SUBS = List.of(
            "info", "freezes", "setstreak", "setday", "reset", "nextday", "missday");
    private static final List<String> COMMON_ZONES = List.of(
            "UTC", "Europe/London", "Europe/Amsterdam", "Europe/Berlin", "Europe/Paris", "America/New_York",
            "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Sao_Paulo", "Asia/Manila",
            "Asia/Singapore", "Asia/Tokyo", "Asia/Kolkata", "Australia/Sydney");

    private final DailyPlugin plugin;

    public DailyCommand(DailyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                help(sender);
                return;
            }
            if (!player.hasPermission(DailyPlugin.PERM_USE)) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            plugin.menu().open(player);
            return;
        }
        if (!sender.hasPermission(DailyPlugin.PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender, args);
            case "freezes" -> freezes(sender, args);
            case "setstreak" -> setStreak(sender, args);
            case "setday" -> setDay(sender, args);
            case "reset" -> reset(sender, args);
            case "nextday" -> nextDay(sender, args);
            case "missday" -> missDay(sender, args);
            case "timezone" -> timezone(sender, args);
            case "reload" -> {
                plugin.loadAll();
                plugin.messages().sendRaw(sender, "<green>Reloaded: <white>" + plugin.daily().days().size() + "</white> reward days.");
            }
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        Messages m = plugin.messages();
        m.sendRaw(sender, "<gold><bold>Wintermark Daily Rewards</bold></gold> <gray>v" + plugin.getPluginMeta().getVersion());
        m.sendRaw(sender, "<yellow>/daily</yellow> <gray>- open the reward calendar");
        if (!sender.hasPermission(DailyPlugin.PERM_ADMIN)) {
            return;
        }
        m.sendRaw(sender, "<yellow>/daily info <player></yellow> <gray>- day, streak, freezes, last claim");
        m.sendRaw(sender, "<yellow>/daily freezes <give|take|set> <player> <amount></yellow> <gray>- streak freezes (protect missed days)");
        m.sendRaw(sender, "<yellow>/daily setstreak <player> <days></yellow> <gray>- restore or change a streak");
        m.sendRaw(sender, "<yellow>/daily setday <player> <day></yellow> <gray>- set the next calendar day");
        m.sendRaw(sender, "<yellow>/daily reset <player></yellow> <gray>- full reset (day, streak, best, total, freezes)");
        m.sendRaw(sender, "<yellow>/daily timezone [zone]</yellow> <gray>- show or set when a new day starts");
        m.sendRaw(sender, "<yellow>/daily nextday <player></yellow> <gray>- test: pretend a day has passed");
        m.sendRaw(sender, "<yellow>/daily missday <player> [days]</yellow> <gray>- test: pretend days were missed");
        m.sendRaw(sender, "<yellow>/daily reload</yellow> <gray>- reload config.yml");
    }

    // ---------------------------------------------------------------- helpers

    /** Finds a player's data. createForOnline: online players without data get an empty entry. */
    private PlayerData.Progress target(CommandSender sender, String name, boolean createForOnline) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData.Progress p = createForOnline ? plugin.data().get(online.getUniqueId(), online.getName())
                    : plugin.data().find(online.getUniqueId());
            if (p != null) {
                return p;
            }
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
            PlayerData.Progress p = op == null ? null : plugin.data().find(op.getUniqueId());
            if (p != null) {
                return p;
            }
        }
        plugin.messages().sendRaw(sender, "<red>No daily reward data for <white>" + name + "</white>.");
        return null;
    }

    private static Integer number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean usage(CommandSender sender, String[] args, int min, String usage) {
        if (args.length < min) {
            plugin.messages().sendRaw(sender, "<red>Usage: " + usage);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- subcommands

    private void info(CommandSender sender, String[] args) {
        if (!usage(sender, args, 2, "/daily info <player>")) {
            return;
        }
        PlayerData.Progress p = target(sender, args[1], false);
        if (p == null) {
            return;
        }
        DailyManager.Status st = plugin.daily().status(p);
        Messages m = plugin.messages();
        m.sendRaw(sender, "<gold>Daily rewards of <white>" + p.name() + "</white>:");
        m.sendRaw(sender, "<gray>Next day: <white>" + st.day() + "</white> <gray>(" + (st.canClaim() ? "<green>can claim now" : "<yellow>claimed today") + "<gray>)");
        m.sendRaw(sender, "<gray>Streak: <white>" + st.streak() + "</white> <gray>| best <white>" + p.best()
                + "</white> | total <white>" + p.total() + "</white> | freezes <white>" + p.freezes() + "</white>");
        m.sendRaw(sender, "<gray>Last claim: <white>" + (p.lastClaim() == null ? "never" : p.lastClaim().toString()) + "</white>"
                + (st.freezesUsed() > 0 ? " <aqua>(" + st.freezesUsed() + " freeze(s) will be used on the next claim)" : "")
                + (st.streakLost() ? " <red>(streak will be lost on the next claim)" : ""));
    }

    private void freezes(CommandSender sender, String[] args) {
        String usage = "/daily freezes <give|take|set> <player> <amount>";
        if (!usage(sender, args, 4, usage)) {
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Integer amount = number(args[3]);
        if (!List.of("give", "take", "set").contains(action) || amount == null || amount < 0) {
            plugin.messages().sendRaw(sender, "<red>Usage: " + usage);
            return;
        }
        PlayerData.Progress p = target(sender, args[2], true);
        if (p == null) {
            return;
        }
        int before = p.freezes();
        int now = plugin.daily().setFreezes(p, switch (action) {
            case "give" -> before + amount;
            case "take" -> before - amount;
            default -> amount;
        });
        plugin.messages().sendRaw(sender, "<green><white>" + p.name() + "</white> now has <white>" + now + "</white> streak freeze(s)."
                + (plugin.daily().maxFreezes() > 0 && now == plugin.daily().maxFreezes() ? " <gray>(max " + now + ")" : ""));
        Player online = Bukkit.getPlayerExact(p.name());
        if (online != null && now > before) {
            plugin.messages().send(online, "freezes-received",
                    Placeholder.unparsed("amount", String.valueOf(now - before)),
                    Placeholder.unparsed("freezes", String.valueOf(now)));
        }
    }

    private void setStreak(CommandSender sender, String[] args) {
        if (!usage(sender, args, 3, "/daily setstreak <player> <days>")) {
            return;
        }
        Integer n = number(args[2]);
        PlayerData.Progress p = n == null || n < 0 ? null : target(sender, args[1], true);
        if (n == null || n < 0) {
            plugin.messages().sendRaw(sender, "<red>Usage: /daily setstreak <player> <days>");
            return;
        }
        if (p == null) {
            return;
        }
        plugin.daily().setStreak(p, n);
        plugin.messages().sendRaw(sender, "<green><white>" + p.name() + "</white>'s streak is now <white>" + n + "</white> day(s)."
                + (p.lastClaim() != null && plugin.daily().status(p).streakLost()
                    ? " <yellow>Note: their last claim is too long ago, so the streak is lost again on the next claim. "
                      + "Give them freezes or use /daily nextday." : ""));
    }

    private void setDay(CommandSender sender, String[] args) {
        int max = plugin.daily().days().size();
        String usage = "/daily setday <player> <day> (1-" + max + ")";
        if (!usage(sender, args, 3, usage)) {
            return;
        }
        Integer day = number(args[2]);
        if (day == null || day < 1 || day > max) {
            plugin.messages().sendRaw(sender, "<red>Usage: " + usage);
            return;
        }
        PlayerData.Progress p = target(sender, args[1], true);
        if (p != null) {
            plugin.daily().setDay(p, day);
            plugin.messages().sendRaw(sender, "<green><white>" + p.name() + "</white> will claim day <white>" + day + "</white> next.");
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (!usage(sender, args, 2, "/daily reset <player>")) {
            return;
        }
        PlayerData.Progress p = target(sender, args[1], false);
        if (p != null) {
            plugin.daily().reset(p);
            plugin.messages().sendRaw(sender, "<green>Reset <white>" + p.name() + "</white>: day 1, streak, best, total and freezes back to 0.");
        }
    }

    private void nextDay(CommandSender sender, String[] args) {
        if (!usage(sender, args, 2, "/daily nextday <player>")) {
            return;
        }
        PlayerData.Progress p = target(sender, args[1], false);
        if (p == null) {
            return;
        }
        plugin.messages().sendRaw(sender, plugin.daily().nextDay(p)
                ? "<green>A day has passed for <white>" + p.name() + "</white> - they can claim day <white>"
                    + plugin.daily().status(p).day() + "</white> now (streak kept)."
                : "<yellow><white>" + p.name() + "</white> can already claim (or never claimed). Nothing changed.");
    }

    private void missDay(CommandSender sender, String[] args) {
        if (!usage(sender, args, 2, "/daily missday <player> [days]")) {
            return;
        }
        int days = plugin.daily().graceDays() + 1;
        if (args.length >= 3) {
            Integer n = number(args[2]);
            if (n == null || n < 1 || n > 365) {
                plugin.messages().sendRaw(sender, "<red>Usage: /daily missday <player> [days 1-365]");
                return;
            }
            days = n;
        }
        PlayerData.Progress p = target(sender, args[1], false);
        if (p == null) {
            return;
        }
        if (!plugin.daily().missDays(p, days)) {
            plugin.messages().sendRaw(sender, "<yellow><white>" + p.name() + "</white> never claimed. Nothing changed.");
            return;
        }
        DailyManager.Status st = plugin.daily().status(p);
        String result = st.streakLost() ? "<red>the streak will be lost"
                : st.freezesUsed() > 0 ? "<aqua>" + st.freezesUsed() + " freeze(s) will save the streak"
                : "<green>the grace days cover it, streak kept";
        plugin.messages().sendRaw(sender, "<green><white>" + p.name() + "</white> now missed <white>" + days
                + "</white> day(s): " + result + "<green>. Next claim is day <white>" + st.day() + "</white>.");
    }

    private void timezone(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (args.length < 2) {
            ZoneId zone = plugin.daily().zone();
            m.sendRaw(sender, "<gray>Timezone: <white>" + zone.getId() + "</white> (now <white>"
                    + ZonedDateTime.now(zone).format(fmt) + "</white>). A new day starts in <white>"
                    + DailyManager.format(plugin.daily().untilReset()) + "</white>.");
            m.sendRaw(sender, "<gray>Change it with <yellow>/daily timezone <zone></yellow>, e.g. Europe/Amsterdam, "
                    + "or <yellow>/daily timezone server</yellow> for the server's own timezone.");
            return;
        }
        String id = args[1];
        String value;
        if (id.equalsIgnoreCase("server")) {
            value = "";
        } else {
            try {
                value = ZoneId.of(id).getId();
            } catch (RuntimeException e) {
                m.sendRaw(sender, "<red>Unknown timezone <white>" + id + "</white>. Use a name like Europe/Amsterdam, "
                        + "America/New_York or Asia/Manila (Tab shows suggestions).");
                return;
            }
        }
        plugin.getConfig().set("timezone", value);
        plugin.saveConfig();
        plugin.loadAll();
        ZoneId zone = plugin.daily().zone();
        m.sendRaw(sender, "<green>Timezone set to <white>" + zone.getId() + "</white>. It is now <white>"
                + ZonedDateTime.now(zone).format(fmt) + "</white> there; a new day starts in <white>"
                + DailyManager.format(plugin.daily().untilReset()) + "</white>.");
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission(DailyPlugin.PERM_ADMIN)) {
            return options;
        }
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            options.addAll(ADMIN_SUBS);
        } else if (first.equals("freezes")) {
            if (args.length == 2) {
                options.addAll(List.of("give", "take", "set"));
            } else if (args.length == 3) {
                addPlayers(options);
            } else if (args.length == 4) {
                options.addAll(List.of("1", "2", "3", "5"));
            }
        } else if (first.equals("timezone") && args.length == 2) {
            options.add("server");
            options.addAll(COMMON_ZONES);
        } else if (args.length == 2 && PLAYER_ARG_SUBS.contains(first)) {
            addPlayers(options);
        } else if (args.length == 3 && first.equals("setday")) {
            for (int d = 1; d <= plugin.daily().days().size(); d++) {
                options.add(String.valueOf(d));
            }
        } else if (args.length == 3 && first.equals("missday")) {
            options.addAll(List.of("1", "2", "3"));
        } else if (args.length == 3 && first.equals("setstreak")) {
            options.addAll(List.of("7", "14", "30"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                result.add(o);
            }
        }
        return result;
    }

    private static void addPlayers(List<String> options) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            options.add(p.getName());
        }
    }
}
