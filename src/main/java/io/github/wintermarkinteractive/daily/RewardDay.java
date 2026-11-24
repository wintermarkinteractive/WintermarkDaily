package io.github.wintermarkinteractive.daily;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One day of the reward calendar (day numbers start at 1). */
public record RewardDay(int day, ItemStack display, List<ItemStack> items, List<String> commands, String message) {
}
