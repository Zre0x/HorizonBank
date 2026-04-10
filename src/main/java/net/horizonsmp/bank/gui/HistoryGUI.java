package net.horizonsmp.bank.gui;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.database.DatabaseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HistoryGUI implements Listener {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter FULL_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final Map<UUID, Integer> playerPage = new ConcurrentHashMap<>();

    public HistoryGUI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<DatabaseManager.Transaction> all =
                plugin.getDatabase().getTransactions(player.getUniqueId(), 0);

        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPage.put(player.getUniqueId(), page);

        String symbol = plugin.getConfig().getString("currency-symbol", "◆");

        Inventory inv = Bukkit.createInventory(null, 54,
                MM.deserialize("<dark_gray>⬛ <gold><bold>История операций</bold></gold> <dark_gray>⬛"));

        ItemStack filler = BankGUI.makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Transaction entries — slots 0..44
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, all.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildTxItem(all.get(i), symbol));
        }

        // Bottom navigation row (slots 45-53)
        if (page > 0) {
            inv.setItem(45, BankGUI.makeItem(Material.ARROW, "<gray>← Пред. страница"));
        }
        inv.setItem(48, BankGUI.makeItem(Material.DARK_OAK_DOOR, "<gray>← В банк"));
        inv.setItem(49, BankGUI.makeItem(Material.BOOK,
                "<yellow>Стр. " + (page + 1) + " / " + totalPages,
                List.of("<gray>Всего операций: <white>" + all.size())));
        if (page < totalPages - 1) {
            inv.setItem(53, BankGUI.makeItem(Material.ARROW, "<gray>След. страница →"));
        }

        player.openInventory(inv);
    }

    private ItemStack buildTxItem(DatabaseManager.Transaction tx, String symbol) {
        String time = FULL_FMT.format(Instant.ofEpochMilli(tx.timestamp));

        Material mat;
        String title;
        String detail;

        switch (tx.type) {
            case "DEPOSIT" -> {
                mat    = Material.LIME_DYE;
                title  = "<green>[+] " + tx.material + " ×" + BankGUI.formatAmount(tx.amount);
                detail = "<gray>Пополнение";
            }
            case "WITHDRAW" -> {
                mat    = Material.RED_DYE;
                title  = "<red>[-] " + tx.material + " ×" + BankGUI.formatAmount(tx.amount);
                detail = "<gray>Снятие";
            }
            case "TRANSFER_OUT" -> {
                mat    = Material.GOLD_NUGGET;
                title  = "<yellow>[→] " + BankGUI.formatAmount(tx.amount) + " " + symbol;
                detail = "<gray>Перевод → <white>" + (tx.counterpart != null ? tx.counterpart : "?");
            }
            case "TRANSFER_IN" -> {
                mat    = Material.EMERALD;
                title  = "<aqua>[←] " + BankGUI.formatAmount(tx.amount) + " " + symbol;
                detail = "<gray>Перевод ← <white>" + (tx.counterpart != null ? tx.counterpart : "?");
            }
            default -> {
                mat    = Material.PAPER;
                title  = "<gray>" + tx.type;
                detail = "";
            }
        }

        return BankGUI.makeItem(mat, title, List.of(detail, "<dark_gray>" + time));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!MM.serialize(e.getView().title()).contains("История операций")) return;
        e.setCancelled(true);

        int page = playerPage.getOrDefault(player.getUniqueId(), 0);

        if (e.getSlot() == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> open(player, page - 1));
        } else if (e.getSlot() == 48) {
            playerPage.remove(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
        } else if (e.getSlot() == 53) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> open(player, page + 1));
        }
    }
}
