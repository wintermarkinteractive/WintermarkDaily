package io.github.wintermarkinteractive.daily;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class DailyListener implements Listener {

    private final DailyPlugin plugin;

    public DailyListener(DailyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DailyMenu.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getRawSlot() == holder.claimSlot()) {
            if (plugin.daily().claim(player)) {
                plugin.menu().fill(player, holder);
            }
        } else {
            plugin.daily().playSound(player, "sounds.click");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DailyMenu.Holder) {
            event.setCancelled(true);
        }
    }

    /** Reminds the player (or opens the menu) when today's reward is waiting. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(DailyPlugin.PERM_USE)) {
            return;
        }
        long delay = Math.max(1, plugin.getConfig().getInt("join.delay-seconds")) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            DailyManager dm = plugin.daily();
            PlayerData.Progress p = plugin.data().get(player.getUniqueId(), player.getName());
            DailyManager.Status st = dm.status(p);
            if (!st.canClaim()) {
                return;
            }
            if (plugin.getConfig().getBoolean("join.open-menu")) {
                plugin.menu().open(player);
            } else if (plugin.getConfig().getBoolean("join.reminder")) {
                plugin.messages().send(player, "join-reminder", dm.placeholders(player, p, st.day()));
                dm.playSound(player, "sounds.reminder");
            }
        }, delay);
    }
}
