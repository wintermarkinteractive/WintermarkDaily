package io.github.wintermarkinteractive.daily;

import io.github.wintermarkinteractive.daily.PlayerData.Progress;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The reward calendar from config.yml and all streak/claim rules. */
public final class DailyManager {

    public static final int MAX_DAYS = 28;

    /** What the calendar looks like for one player right now. */
    public record Status(int day, boolean canClaim, boolean streakLost, int streak, int freezesUsed) {
    }

    private final DailyPlugin plugin;
    /** Extra reward when the streak reaches a number of days. */
    public record StreakBonus(int streak, List<ItemStack> items, List<String> commands, String message, boolean broadcast) {
    }

    private final List<RewardDay> days = new ArrayList<>();
    private final java.util.TreeMap<Integer, StreakBonus> bonuses = new java.util.TreeMap<>();
    private ZoneId zone = ZoneId.systemDefault();
    private boolean resetOnMiss = true;
    private int graceDays;
    private boolean loop = true;

    public DailyManager(DailyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        days.clear();
        String tz = plugin.getConfig().getString("timezone", "");
        try {
            zone = tz == null || tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Unknown timezone '" + tz + "' - using the server's timezone.");
            zone = ZoneId.systemDefault();
        }
        resetOnMiss = !"KEEP".equalsIgnoreCase(plugin.getConfig().getString("streak.on-miss", "RESET"));
        graceDays = Math.max(0, plugin.getConfig().getInt("streak.grace-days"));
        loop = !"REPEAT-LAST".equalsIgnoreCase(plugin.getConfig().getString("streak.after-last-day", "LOOP"));

        loadBonuses();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("days");
        if (root == null) {
            plugin.getLogger().warning("config.yml has no 'days' section.");
            return;
        }
        for (int d = 1; d <= MAX_DAYS; d++) {
            ConfigurationSection s = root.getConfigurationSection(String.valueOf(d));
            if (s == null) {
                break; // days must be 1, 2, 3, ... without gaps
            }
            String where = "days." + d;
            List<ItemStack> items = new ArrayList<>();
            for (String line : s.getStringList("items")) {
                ItemStack item = parseItem(line, where);
                if (item != null) {
                    items.add(item);
                }
            }
            ItemStack display = s.isString("display") ? parseItem(s.getString("display"), where) : null;
            if (display == null) {
                display = items.isEmpty() ? new ItemStack(Material.CHEST) : items.get(0).clone();
            }
            String name = s.getString("display-name", "<yellow>Day " + d);
            List<String> lore = s.getStringList("display-lore");
            Messages m = plugin.messages();
            display.editMeta(meta -> {
                meta.displayName(noItalic(m.parse(name)));
                List<Component> l = new ArrayList<>();
                for (String line : lore) {
                    l.add(noItalic(m.parse(line)));
                }
                meta.lore(l);
            });
            days.add(new RewardDay(d, display, items, s.getStringList("commands"), s.getString("message", "")));
        }
        if (root.getKeys(false).size() > days.size()) {
            plugin.getLogger().warning("Only days 1-" + days.size() + " were loaded: days must be numbered 1, 2, 3, ... "
                    + "without gaps (max " + MAX_DAYS + ").");
        }
    }

    private void loadBonuses() {
        bonuses.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("streak-bonuses");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            int streak;
            try {
                streak = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                streak = -1;
            }
            if (s == null || streak < 1) {
                plugin.getLogger().warning("streak-bonuses." + key + ": the key must be a number of days (1, 2, 3, ...).");
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (String line : s.getStringList("items")) {
                ItemStack item = parseItem(line, "streak-bonuses." + key);
                if (item != null) {
                    items.add(item);
                }
            }
            bonuses.put(streak, new StreakBonus(streak, items, s.getStringList("commands"),
                    s.getString("message", ""), s.getBoolean("broadcast", false)));
        }
    }

    /** The next streak bonus above this streak, or null. */
    public StreakBonus nextBonus(int streak) {
        java.util.Map.Entry<Integer, StreakBonus> e = bonuses.higherEntry(streak);
        return e == null ? null : e.getValue();
    }

    static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private ItemStack parseItem(String line, String where) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null || !mat.isItem() || mat.isAir()) {
            plugin.getLogger().warning("Unknown item '" + parts[0] + "' in " + where);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Bad amount in '" + line + "' in " + where);
            }
        }
        return new ItemStack(mat, amount);
    }

    public List<RewardDay> days() {
        return days;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** Time until the next day starts (midnight in the configured timezone). */
    public Duration untilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return Duration.between(now, midnight);
    }

    public static String format(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long h = s / 3600;
        long m = (s % 3600) / 60;
        return h > 0 ? String.format(Locale.ROOT, "%dh %dm", h, m) : String.format(Locale.ROOT, "%dm", Math.max(1, m));
    }

    /** The calendar state for this progress, applying the missed-day rules (without changing anything). */
    public Status status(Progress p) {
        int day = Math.min(Math.max(1, p.nextDay), Math.max(1, days.size()));
        LocalDate today = today();
        if (p.lastClaim == null) {
            return new Status(day, true, false, 0, 0);
        }
        if (!p.lastClaim.isBefore(today)) {
            return new Status(day, false, false, p.streak, 0); // already claimed today
        }
        // Days without a claim between the last claim and today, minus the free grace days.
        long missed = java.time.temporal.ChronoUnit.DAYS.between(p.lastClaim, today) - 1 - graceDays;
        if (missed <= 0) {
            return new Status(day, true, false, p.streak, 0);
        }
        if (p.streak > 0 && p.freezes >= missed) {
            return new Status(day, true, false, p.streak, (int) missed); // freezes save the streak
        }
        return new Status(resetOnMiss ? 1 : day, true, p.streak > 0, 0, 0);
    }

    /** Claims today's reward. Returns false when it was already claimed today. */
    public boolean claim(Player player) {
        if (days.isEmpty()) {
            return false;
        }
        Progress p = plugin.data().get(player.getUniqueId(), player.getName());
        Status st = status(p);
        if (!st.canClaim()) {
            return false;
        }
        RewardDay reward = days.get(st.day() - 1);
        p.freezes -= st.freezesUsed();
        p.streak = st.streak() + 1;
        p.best = Math.max(p.best, p.streak);
        p.total++;
        p.lastClaim = today();
        p.nextDay = st.day() >= days.size() ? (loop ? 1 : days.size()) : st.day() + 1;
        plugin.data().save();

        give(player, reward, p);
        if (st.streakLost()) {
            plugin.messages().send(player, "streak-lost");
        }
        if (st.freezesUsed() > 0) {
            plugin.messages().send(player, "freezes-used",
                    Placeholder.unparsed("used", String.valueOf(st.freezesUsed())),
                    Placeholder.unparsed("freezes", String.valueOf(p.freezes)));
        }
        StreakBonus bonus = bonuses.get(p.streak);
        if (bonus != null) {
            giveBonus(player, bonus, p);
        }
        playSound(player, "sounds.claim");
        return true;
    }

    private void give(Player player, RewardDay reward, Progress p) {
        for (ItemStack item : reward.items()) {
            for (ItemStack rest : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        for (String cmd : reward.commands()) {
            String line = cmd.replace("<player>", player.getName()).replace("<day>", String.valueOf(reward.day()));
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        plugin.messages().sendRaw(player, reward.message(), placeholders(player, p, reward.day()));
    }

    private void giveBonus(Player player, StreakBonus bonus, Progress p) {
        for (ItemStack item : bonus.items()) {
            for (ItemStack rest : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        for (String cmd : bonus.commands()) {
            String line = cmd.replace("<player>", player.getName()).replace("<streak>", String.valueOf(bonus.streak()));
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        Messages m = plugin.messages();
        TagResolver[] ph = placeholders(player, p, p.nextDay);
        m.sendRaw(player, bonus.message(), ph);
        if (bonus.broadcast()) {
            String text = m.raw("bonus-broadcast");
            if (!text.isEmpty()) {
                Bukkit.getServer().broadcast(m.parse(m.raw("prefix") + text, ph));
            }
        }
        playSound(player, "sounds.bonus");
    }

    public TagResolver[] placeholders(Player player, Progress p, int day) {
        return new TagResolver[]{
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("day", String.valueOf(day)),
                Placeholder.unparsed("days", String.valueOf(days.size())),
                Placeholder.unparsed("streak", String.valueOf(status(p).streak())),
                Placeholder.unparsed("next-bonus", nextBonus(status(p).streak()) == null ? "-"
                        : String.valueOf(nextBonus(status(p).streak()).streak())),
                Placeholder.unparsed("bonus-left", nextBonus(status(p).streak()) == null ? "-"
                        : String.valueOf(nextBonus(status(p).streak()).streak() - status(p).streak())),
                Placeholder.unparsed("best", String.valueOf(p.best)),
                Placeholder.unparsed("total", String.valueOf(p.total)),
                Placeholder.unparsed("freezes", String.valueOf(p.freezes)),
                Placeholder.unparsed("time", format(untilReset()))
        };
    }

    public void playSound(Player player, String path) {
        String key = plugin.getConfig().getString(path, "");
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Bad sound '" + key + "' at " + path + " in config.yml.");
        }
    }

    // ---------------------------------------------------------------- admin / testing tools

    /** Full reset: day 1, no streak, best streak and total claims back to 0. */
    public void reset(Progress p) {
        p.nextDay = 1;
        p.lastClaim = null;
        p.streak = 0;
        p.best = 0;
        p.total = 0;
        p.freezes = 0;
        plugin.data().save();
    }

    public void setDay(Progress p, int day) {
        p.nextDay = Math.max(1, Math.min(day, days.size()));
        plugin.data().save();
    }

    /** Pretends a day has passed: the player can claim again and keeps the streak. False if they can claim already. */
    public boolean nextDay(Progress p) {
        if (p.lastClaim == null || p.lastClaim.isBefore(today())) {
            return false;
        }
        p.lastClaim = today().minusDays(1);
        plugin.data().save();
        return true;
    }

    /** Pretends the player missed this many days. False if they never claimed. */
    public boolean missDays(Progress p, int missed) {
        if (p.lastClaim == null) {
            return false;
        }
        p.lastClaim = today().minusDays(1L + Math.max(1, missed));
        plugin.data().save();
        return true;
    }

    public int graceDays() {
        return graceDays;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Max freezes a player can hold (0 = no limit). */
    public int maxFreezes() {
        return Math.max(0, plugin.getConfig().getInt("streak.max-freezes"));
    }

    /** Changes a player's freezes, respecting max-freezes. Returns the new amount. */
    public int setFreezes(Progress p, int amount) {
        int max = maxFreezes();
        p.freezes = Math.max(0, max > 0 ? Math.min(amount, max) : amount);
        plugin.data().save();
        return p.freezes;
    }

    public void setStreak(Progress p, int streak) {
        p.streak = Math.max(0, streak);
        p.best = Math.max(p.best, p.streak);
        plugin.data().save();
    }
}
