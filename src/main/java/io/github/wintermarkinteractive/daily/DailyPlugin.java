package io.github.wintermarkinteractive.daily;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DailyPlugin extends JavaPlugin {

    public static final String PERM_USE = "wintermark.daily.use";
    public static final String PERM_ADMIN = "wintermark.daily.admin";

    private Messages messages;
    private PlayerData data;
    private DailyManager daily;
    private DailyMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        data = new PlayerData(this);
        daily = new DailyManager(this);
        menu = new DailyMenu(this);

        loadAll();
        data.load();

        getServer().getPluginManager().registerEvents(new DailyListener(this), this);
        registerCommand("daily", "Wintermark Daily Rewards", List.of("dailyrewards"), new DailyCommand(this));
        getLogger().info("Loaded " + daily.days().size() + " reward days.");
    }

    @Override
    public void onDisable() {
        if (data != null) {
            data.save();
        }
    }

    /** (Re)loads config.yml. Player data is only loaded on startup. */
    public void loadAll() {
        reloadConfig();
        addMissingConfigKeys();
        messages.load();
        daily.load();
    }

    /**
     * Adds new settings to an existing config.yml. "days" (and "streak-bonuses" once it exists) are skipped:
     * owners may change or remove entries there and we must not bring them back.
     */
    private void addMissingConfigKeys() {
        FileConfiguration config = getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        // Checked before the loop: once the section exists in the owner's file, its entries are theirs.
        boolean bonusesExist = config.isSet("streak-bonuses");
        for (String key : defaults.getKeys(true)) {
            boolean ownerList = key.equals("days") || key.startsWith("days.")
                    || (key.startsWith("streak-bonuses.") && bonusesExist);
            if (ownerList || config.isSet(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                config.createSection(key);
            } else {
                config.set(key, defaults.get(key));
            }
            config.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            saveConfig();
            reloadConfig();
            getLogger().info("Added new settings to config.yml.");
        }
    }

    public Messages messages() { return messages; }
    public PlayerData data() { return data; }
    public DailyManager daily() { return daily; }
    public DailyMenu menu() { return menu; }
}
