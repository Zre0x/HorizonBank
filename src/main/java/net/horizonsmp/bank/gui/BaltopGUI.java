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

import net.horizonsmp.bank.database.DatabaseManager;

import java.util.List;

public class BaltopGUI implements Listener {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final Material[] RANK_MATERIALS = {
            Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK,
            Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.GRAY_WOOL, Material.DARK_OAK_PLANKS, Material.OAK_PLANKS
    };
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

    public BaltopGUI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        String symbol = plugin.getConfig().getString("currency-symbol", "◆");
        List<DatabaseManager.TopEntry> top = plugin.getDatabase().getTopBalances(10);

        Inventory inv = Bukkit.createInventory(null, 36,
                MM.deserialize("<dark_gray>⬛ <yellow><bold>Топ богачей</bold></yellow> <dark_gray>⬛"));

        ItemStack filler = BankGUI.makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        for (int i = 0; i < top.size(); i++) {
            DatabaseManager.TopEntry entry = top.get(i);
            String rank = switch (i) {
                case 0 -> "<gold>🥇 #1";
                case 1 -> "<gray>🥈 #2";
                case 2 -> "<#cd7f32>🥉 #3";
                default -> "<dark_gray>#" + (i + 1);
            };
            String displayName = entry.hidden ? "<dark_gray>???" : "<white>" + entry.name;
            String balanceLine = entry.hidden
                    ? "<gray>Баланс: <dark_gray>скрыт"
                    : "<gray>Баланс: <white>" + BankGUI.formatAmount(entry.total) + " " + symbol;
            inv.setItem(SLOTS[i], BankGUI.makeItem(RANK_MATERIALS[i],
                    rank + " " + displayName,
                    List.of(balanceLine)));
        }

        inv.setItem(31, BankGUI.makeItem(Material.ARROW, "<gray>← Назад"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!MM.serialize(e.getView().title()).contains("Топ богачей")) return;
        e.setCancelled(true);
        if (e.getSlot() == 31) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
        }
    }
}
