package net.horizonsmp.bank.gui;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BankGUI implements Listener {

    private final HorizonBank plugin;
    static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    public BankGUI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        double balance = plugin.getDatabase().getBalance(player.getUniqueId());
        boolean hidden = plugin.getDatabase().isHidden(player.getUniqueId());
        String symbol = plugin.getConfig().getString("currency-symbol", "◆");

        // Per-item balance lines
        Map<String, Double> itemBalances = plugin.getDatabase().getItemBalances(player.getUniqueId());
        List<String> balLines;
        if (itemBalances.isEmpty()) {
            balLines = List.of("<gray>0 " + symbol);
        } else {
            balLines = itemBalances.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> "<gray>" + e.getKey() + ": <white>" + formatAmount(e.getValue()))
                    .toList();
        }

        // Last 5 transactions for history hover
        List<DatabaseManager.Transaction> recent = plugin.getDatabase().getTransactions(player.getUniqueId(), 5);
        List<String> histLore = new ArrayList<>();
        if (recent.isEmpty()) {
            histLore.add("<gray>Нет операций");
        } else {
            for (DatabaseManager.Transaction tx : recent) {
                String time = SHORT_FMT.format(Instant.ofEpochMilli(tx.timestamp));
                String line = switch (tx.type) {
                    case "DEPOSIT"       -> "<green>[+]</green> <gray>" + tx.material + " ×" + formatAmount(tx.amount);
                    case "WITHDRAW"      -> "<red>[-]</red> <gray>" + tx.material + " ×" + formatAmount(tx.amount);
                    case "TRANSFER_OUT"  -> "<yellow>[→]</yellow> <gray>" + formatAmount(tx.amount) + symbol + " → " + tx.counterpart;
                    case "TRANSFER_IN"   -> "<aqua>[←]</aqua> <gray>" + formatAmount(tx.amount) + symbol + " ← " + tx.counterpart;
                    default              -> "<gray>" + tx.type;
                };
                histLore.add(line + " <dark_gray>" + time);
            }
        }
        histLore.add("");
        histLore.add("<dark_gray>Нажми для полной истории");

        Inventory inv = Bukkit.createInventory(null, 27,
                MM.deserialize("<dark_gray>⬛ <gold><bold>HorizonBank</bold></gold> <dark_gray>⬛"));

        ItemStack filler = makeItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Slot 4 — balance (always visible to owner)
        inv.setItem(4, makeItem(Material.GOLD_NUGGET, "<gold><bold>Баланс</bold>", balLines));

        // Row 1 buttons
        inv.setItem(11, makeItem(Material.LIME_DYE, "<green><bold>Пополнить</bold>",
                List.of("<gray>Сдать предметы из инвентаря в банк")));
        inv.setItem(13, makeItem(Material.RED_DYE, "<red><bold>Снять</bold>",
                List.of("<gray>Получить предметы обратно")));
        inv.setItem(15, makeItem(Material.PAPER, "<aqua><bold>Перевести</bold>",
                List.of("<gray>/pay <игрок> <сумма>")));

        // Row 2 buttons
        inv.setItem(20, makeItem(Material.CLOCK, "<yellow><bold>История</bold>", histLore));
        inv.setItem(22, makeItem(Material.NETHER_STAR, "<yellow><bold>Топ богачей</bold>",
                List.of("<gray>Топ-10 по балансу")));

        // Privacy toggle — slot 26
        String privacyStatus = hidden ? "<red>Скрыт" : "<green>Виден";
        inv.setItem(26, makeItem(hidden ? Material.SOUL_LANTERN : Material.LANTERN,
                "<yellow><bold>Приватность</bold>",
                List.of("<gray>Баланс в топе и профиле: " + privacyStatus,
                        "",
                        "<dark_gray>ЛКМ — переключить")));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Component title = e.getView().title();
        if (!MM.serialize(title).contains("HorizonBank")) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        switch (e.getSlot()) {
            case 11 -> { player.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> plugin.getDepositGUI().open(player)); }
            case 13 -> { player.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> plugin.getWithdrawGUI().open(player)); }
            case 15 -> { player.closeInventory(); player.sendMessage(MM.deserialize("<gray>Используй: <white>/pay <игрок> <сумма>")); }
            case 20 -> { player.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> plugin.getHistoryGUI().open(player, 0)); }
            case 22 -> { player.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> plugin.getBaltopGUI().open(player)); }
            case 26 -> {
                boolean hidden = plugin.getDatabase().isHidden(player.getUniqueId());
                plugin.getDatabase().setHidden(player.getUniqueId(), !hidden);
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> open(player));
            }
        }
    }

    /** Create item with default stack size 1 */
    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        return makeItem(mat, 1, name, lore);
    }

    public static ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, 1, name, null);
    }

    /** Create item with custom stack size (used for visual count in calculator panels) */
    public static ItemStack makeItem(Material mat, int count, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(count, 64)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic>" + name));
        if (lore != null) {
            meta.lore(lore.stream().map(l -> MM.deserialize("<!italic>" + l)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String formatAmount(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }
}
