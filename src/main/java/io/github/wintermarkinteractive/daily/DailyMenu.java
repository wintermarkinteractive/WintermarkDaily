package io.github.wintermarkinteractive.daily;

import io.github.wintermarkinteractive.daily.DailyManager.Status;
import io.github.wintermarkinteractive.daily.PlayerData.Progress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The calendar menu: one slot per day, 7 per row, plus an info item. */
public final class DailyMenu {

    /** Marks our inventory and remembers which slot claims today's reward. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private int claimSlot = -1;

        public int claimSlot() {
            return claimSlot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DailyPlugin plugin;

    public DailyMenu(DailyPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        DailyManager dm = plugin.daily();
        int count = dm.days().size();
        if (count == 0) {
            plugin.messages().sendRaw(player, "<red>No daily rewards are configured.");
            return;
        }
        int dayRows = (count + 6) / 7;
        int rows = Math.min(6, dayRows + 2);
        Holder holder = new Holder();
        Messages m = plugin.messages();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, m.parse(m.raw("menu-title")));
        holder.inventory = inv;
        fill(player, holder);
        player.openInventory(inv);
    }

    /** Redraws an open menu (after claiming). */
    public void fill(Player player, Holder holder) {
        DailyManager dm = plugin.daily();
        Messages m = plugin.messages();
        Inventory inv = holder.inventory;
        Progress p = plugin.data().get(player.getUniqueId(), player.getName());
        Status st = dm.status(p);
        int count = dm.days().size();
        // After the last day was claimed today the cycle shows as complete instead of empty.
        boolean cycleDone = !st.canClaim() && st.day() == 1 && p.total() > 0 && p.lastClaim() != null;
        TagResolver[] ph = dm.placeholders(player, p, st.day());

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        holder.claimSlot = -1;
        for (int i = 0; i < count; i++) {
            int day = i + 1;
            int slot = (1 + i / 7) * 9 + 1 + (i % 7);
            if (slot >= inv.getSize() - 9) {
                break;
            }
            boolean claimed = cycleDone || day < st.day();
            ItemStack item;
            if (claimed) {
                item = pane(Material.LIME_STAINED_GLASS_PANE,
                        DailyManager.noItalic(m.parse(m.raw("day-claimed-name"), Placeholder.unparsed("day", String.valueOf(day)))));
            } else {
                item = dm.days().get(i).display().clone();
                List<Component> lore = item.lore() == null ? new ArrayList<>() : new ArrayList<>(item.lore());
                lore.add(Component.empty());
                if (day == st.day()) {
                    String key = st.canClaim() ? "day-claim-lore" : "day-tomorrow-lore";
                    lore.add(DailyManager.noItalic(m.parse(m.raw(key), ph)));
                    if (st.canClaim()) {
                        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                        holder.claimSlot = slot;
                    }
                } else {
                    lore.add(DailyManager.noItalic(m.parse(m.raw("day-locked-lore"),
                            Placeholder.unparsed("day", String.valueOf(day)))));
                }
                item.lore(lore);
            }
            inv.setItem(slot, item);
        }

        // Info item in the middle of the bottom row.
        ItemStack info = new ItemStack(Material.CLOCK);
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("messages.info-lore")) {
            lore.add(DailyManager.noItalic(m.parse(line, ph)));
        }
        if (p.freezes() > 0 && !m.raw("info-freezes").isEmpty()) {
            lore.add(DailyManager.noItalic(m.parse(m.raw("info-freezes"), ph)));
        }
        if (dm.nextBonus(st.streak()) != null && !m.raw("info-bonus").isEmpty()) {
            lore.add(DailyManager.noItalic(m.parse(m.raw("info-bonus"), ph)));
        }
        info.editMeta(meta -> {
            meta.displayName(DailyManager.noItalic(m.parse(m.raw("info-name"), ph)));
            meta.lore(lore);
        });
        inv.setItem(inv.getSize() - 5, info);
    }

    private static ItemStack pane(Material material, Component name) {
        ItemStack pane = new ItemStack(material);
        pane.editMeta(meta -> meta.displayName(name));
        return pane;
    }
}
