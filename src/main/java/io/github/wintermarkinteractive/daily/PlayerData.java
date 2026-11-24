package io.github.wintermarkinteractive.daily;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Progress per player, saved in plugins/WintermarkDaily/data.yml. */
public final class PlayerData {

    /** One player's progress. */
    public static final class Progress {
        String name;
        int nextDay = 1;        // calendar day the player claims next (1-based)
        LocalDate lastClaim;    // null = never claimed
        int streak;             // days in a row
        int best;               // best streak ever
        int total;              // total claims
        int freezes;            // streak freezes: each one protects the streak for one missed day

        public String name() { return name; }
        public int nextDay() { return nextDay; }
        public LocalDate lastClaim() { return lastClaim; }
        public int streak() { return streak; }
        public int best() { return best; }
        public int total() { return total; }
        public int freezes() { return freezes; }
    }

    private final DailyPlugin plugin;
    private final File file;
    private final Map<UUID, Progress> data = new HashMap<>();

    public PlayerData(DailyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Progress p = new Progress();
                p.name = s.getString("name", "?");
                p.nextDay = Math.max(1, s.getInt("next-day", 1));
                String last = s.getString("last-claim");
                p.lastClaim = last == null || last.isEmpty() ? null : LocalDate.parse(last);
                p.streak = s.getInt("streak");
                p.best = s.getInt("best");
                p.total = s.getInt("total");
                p.freezes = Math.max(0, s.getInt("freezes"));
                data.put(UUID.fromString(key), p);
            } catch (IllegalArgumentException | DateTimeParseException e) {
                plugin.getLogger().warning("Skipping bad entry '" + key + "' in data.yml: " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Progress> e : data.entrySet()) {
            Progress p = e.getValue();
            String path = "players." + e.getKey() + ".";
            yaml.set(path + "name", p.name);
            yaml.set(path + "next-day", p.nextDay);
            yaml.set(path + "last-claim", p.lastClaim == null ? "" : p.lastClaim.toString());
            yaml.set(path + "streak", p.streak);
            yaml.set(path + "best", p.best);
            yaml.set(path + "total", p.total);
            yaml.set(path + "freezes", p.freezes);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public Progress get(UUID id, String name) {
        Progress p = data.computeIfAbsent(id, k -> new Progress());
        if (name != null) {
            p.name = name;
        }
        return p;
    }

    /** Null when the player has no data yet. */
    public Progress find(UUID id) {
        return data.get(id);
    }

    public Map<UUID, Progress> all() {
        return data;
    }
}
