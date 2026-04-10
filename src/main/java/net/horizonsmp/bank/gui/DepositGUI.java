package net.horizonsmp.bank.gui;

import net.horizonsmp.bank.HorizonBank;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DepositGUI implements Listener {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public DepositGUI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        // Count currency items in player inventory
        Map<Material, Long> found = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (plugin.getCurrencyItems().containsKey(item.getType())) {
                found.merge(item.getType(), (long) item.getAmount(), Long::sum);
            }
        }

        String symbol = plugin.getConfig().getString("currency-symbol", "◆");
        List<String> lines = new ArrayList<>();
        if (found.isEmpty()) {
            lines.add("<red>Нет предметов для пополнения");
        } else {
            for (Map.Entry<Material, Long> entry : found.entrySet()) {
                Double value = plugin.getCurrencyItems().get(entry.getKey());
                lines.add("<gray>" + entry.getKey().name() + ": <white>" + entry.getValue()
                        + " <gray>= <white>" + BankGUI.formatAmount(entry.getValue() * value) + " " + symbol);
            }
        }

        Inventory inv = Bukkit.createInventory(null, 27,
                MM.deserialize("<dark_gray>⬛ <green><bold>Пополнить</bold></green> <dark_gray>⬛"));

        ItemStack filler = BankGUI.makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(13, BankGUI.makeItem(Material.LIME_WOOL,
                "<green><bold>Положить всё из инвентаря</bold>", lines));
        inv.setItem(22, BankGUI.makeItem(Material.ARROW, "<gray>← Назад"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!MM.serialize(e.getView().title()).contains("Пополнить")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 13) {
            double totalDeposited = 0;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null) continue;
                Double value = plugin.getCurrencyItems().get(item.getType());
                if (value == null) continue;
                long amount = item.getAmount();
                plugin.getDatabase().addItemBalance(player.getUniqueId(), item.getType().name(), amount);
                plugin.getDatabase().logTransaction(player.getUniqueId(), "DEPOSIT", item.getType().name(), amount, null);
                totalDeposited += amount * value;
                player.getInventory().setItem(i, null);
            }
            String symbol = plugin.getConfig().getString("currency-symbol", "◆");
            if (totalDeposited > 0) {
                player.sendMessage(MM.deserialize(plugin.msg("deposit-success")
                        .replace("<amount>", BankGUI.formatAmount(totalDeposited))
                        .replace("<symbol>", symbol)));
            } else {
                player.sendMessage(MM.deserialize("<gray>Нет предметов для пополнения."));
            }
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
        } else if (e.getSlot() == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
        }
    }
}
