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
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WithdrawGUI implements Listener {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Per-player calculator state
    private final Map<UUID, String>  pendingMaterial = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentAmount   = new ConcurrentHashMap<>();

    // Calculator layout — middle row slots 9-17
    //  9=−all  10=−16  11=−8  12=−1  13=ORE  14=+1  15=+8  16=+16  17=+all
    private static final int SLOT_ORE   = 13;
    private static final int SLOT_TAKE  = 22;  // bottom center
    private static final int SLOT_BACK  = 26;  // bottom right

    public WithdrawGUI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Material selection list (shown only when >1 currency type has balance)
    // ──────────────────────────────────────────────────────────────────────

    public void open(Player player) {
        Map<String, Double> balances = plugin.getDatabase().getItemBalances(player.getUniqueId());
        List<Map.Entry<String, Double>> entries = balances.entrySet().stream()
                .filter(e -> e.getValue() > 0).toList();

        if (entries.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Баланс пуст."));
            return;
        }
        // Single material → go straight to calculator
        if (entries.size() == 1) {
            Map.Entry<String, Double> e = entries.get(0);
            openCalculator(player, e.getKey(), e.getValue());
            return;
        }

        // Multiple materials — show picker
        Inventory inv = Bukkit.createInventory(null, 27,
                MM.deserialize("<dark_gray>⬛ <red><bold>Снять</bold></red> <dark_gray>⬛"));
        ItemStack filler = BankGUI.makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(entries.size(), slots.length); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            try {
                Material mat = Material.valueOf(e.getKey());
                long units = (long) Math.floor(e.getValue());
                inv.setItem(slots[i], BankGUI.makeItem(mat, "<white>" + e.getKey(),
                        List.of("<gray>В банке: <white>" + units + " шт.",
                                "",
                                "<dark_gray>Нажми для выбора количества")));
            } catch (IllegalArgumentException ignored) {}
        }
        inv.setItem(22, BankGUI.makeItem(Material.ARROW, "<gray>← Назад"));
        player.openInventory(inv);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Calculator GUI
    // ──────────────────────────────────────────────────────────────────────

    public void openCalculator(Player player, String material, double bankBalance) {
        pendingMaterial.put(player.getUniqueId(), material);
        currentAmount.putIfAbsent(player.getUniqueId(), 0);

        Inventory inv = Bukkit.createInventory(null, 27,
                MM.deserialize("<dark_gray>⬛ <red><bold>Снять: " + material + "</bold></red> <dark_gray>⬛"));
        ItemStack filler = BankGUI.makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        fillCalculator(inv, material, bankBalance, currentAmount.get(player.getUniqueId()));
        player.openInventory(inv);
    }

    /** Fills (or refreshes) all calculator slots in the given inventory */
    private void fillCalculator(Inventory inv, String material, double bankBalance, int amount) {
        long maxAvail = Math.min((long) Math.floor(bankBalance), 64);

        // ─ Left red panels: −all(64), −16, −8, −1 ─
        inv.setItem(9,  BankGUI.makeItem(Material.RED_STAINED_GLASS_PANE, 64,
                "<red>− всё", List.of("<gray>Сбросить до 0")));
        inv.setItem(10, BankGUI.makeItem(Material.RED_STAINED_GLASS_PANE, 16,
                "<red>−16", List.of("<gray>Уменьшить на 16")));
        inv.setItem(11, BankGUI.makeItem(Material.RED_STAINED_GLASS_PANE, 8,
                "<red>−8",  List.of("<gray>Уменьшить на 8")));
        inv.setItem(12, BankGUI.makeItem(Material.RED_STAINED_GLASS_PANE, 1,
                "<red>−1",  List.of("<gray>Уменьшить на 1")));

        // ─ Center ore ─
        try {
            Material mat = Material.valueOf(material);
            String oreName = amount > 0 ? "<white>" + amount + " шт." : "<gray>Выберите количество";
            inv.setItem(SLOT_ORE, BankGUI.makeItem(mat, Math.max(1, amount), oreName,
                    List.of("<gray>В банке: <white>" + BankGUI.formatAmount(bankBalance) + " шт.",
                            "<dark_gray>Макс. за раз: " + maxAvail)));
        } catch (IllegalArgumentException ignored) {}

        // ─ Right green panels: +1, +8, +16, +all(64) ─
        inv.setItem(14, BankGUI.makeItem(Material.LIME_STAINED_GLASS_PANE, 1,
                "<green>+1",  List.of("<gray>Увеличить на 1")));
        inv.setItem(15, BankGUI.makeItem(Material.LIME_STAINED_GLASS_PANE, 8,
                "<green>+8",  List.of("<gray>Увеличить на 8")));
        inv.setItem(16, BankGUI.makeItem(Material.LIME_STAINED_GLASS_PANE, 16,
                "<green>+16", List.of("<gray>Увеличить на 16")));
        inv.setItem(17, BankGUI.makeItem(Material.LIME_STAINED_GLASS_PANE, 64,
                "<green>+ всё", List.of("<gray>Максимум (" + maxAvail + ")")));

        // ─ Take button ─
        if (amount > 0 && amount <= maxAvail) {
            inv.setItem(SLOT_TAKE, BankGUI.makeItem(Material.EMERALD, "<green><bold>Снять (" + amount + " шт.)</bold>",
                    List.of("<gray>Нажми, чтобы получить предметы")));
        } else {
            inv.setItem(SLOT_TAKE, BankGUI.makeItem(Material.BARRIER, "<dark_gray>Снять",
                    List.of(amount == 0 ? "<gray>Выберите количество" : "<red>Недостаточно средств")));
        }

        // ─ Back button ─
        inv.setItem(SLOT_BACK, BankGUI.makeItem(Material.ARROW, "<gray>← Назад"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Click handler
    // ──────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = MM.serialize(e.getView().title());

        // ── Calculator ──────────────────────────────────────────────────
        if (title.contains("Снять: ")) {
            e.setCancelled(true);

            String material = pendingMaterial.get(player.getUniqueId());
            if (material == null) return;

            double bankBalance = plugin.getDatabase().getItemBalances(player.getUniqueId())
                    .getOrDefault(material, 0.0);
            long maxAvail = Math.min((long) Math.floor(bankBalance), 64);
            int amt = currentAmount.getOrDefault(player.getUniqueId(), 0);

            switch (e.getSlot()) {
                // Red: decrease
                case 9  -> amt = 0;
                case 10 -> amt = Math.max(0, amt - 16);
                case 11 -> amt = Math.max(0, amt - 8);
                case 12 -> amt = Math.max(0, amt - 1);
                // Green: increase
                case 14 -> amt = (int) Math.min(amt + 1,  maxAvail);
                case 15 -> amt = (int) Math.min(amt + 8,  maxAvail);
                case 16 -> amt = (int) Math.min(amt + 16, maxAvail);
                case 17 -> amt = (int) maxAvail;
                // Take
                case 22 -> {
                    if (amt <= 0 || amt > maxAvail) return;
                    handleWithdraw(player, material, amt, bankBalance);
                    return; // don't re-render amount — state preserved, balance updated in-place
                }
                // Back
                case 26 -> {
                    pendingMaterial.remove(player.getUniqueId());
                    currentAmount.remove(player.getUniqueId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
                    return;
                }
                default -> { return; }
            }

            currentAmount.put(player.getUniqueId(), amt);
            // Refresh calculator in-place
            double freshBalance = plugin.getDatabase().getItemBalances(player.getUniqueId())
                    .getOrDefault(material, 0.0);
            fillCalculator(e.getView().getTopInventory(), material, freshBalance, amt);
            return;
        }

        // ── Material picker ─────────────────────────────────────────────
        if (!title.contains("Снять")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBankGUI().open(player));
            return;
        }

        Material clicked = e.getCurrentItem().getType();
        if (clicked == Material.GRAY_STAINED_GLASS_PANE || clicked == Material.ARROW) return;

        String matName = clicked.name();
        double bal = plugin.getDatabase().getItemBalances(player.getUniqueId()).getOrDefault(matName, 0.0);
        if (bal <= 0) return;

        currentAmount.put(player.getUniqueId(), 0);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> openCalculator(player, matName, bal));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Withdraw logic
    // ──────────────────────────────────────────────────────────────────────

    private void handleWithdraw(Player player, String material, int amount, double bankBalance) {
        Material mat;
        try { mat = Material.valueOf(material); }
        catch (IllegalArgumentException ex) { return; }

        // Check inventory space
        if (!hasRoomFor(player, mat, amount)) {
            player.sendMessage(MM.deserialize("<red>Недостаточно места в инвентаре."));
            return;
        }

        double newBalance = bankBalance - amount;
        plugin.getDatabase().setItemBalance(player.getUniqueId(), material, newBalance);

        // Give items
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(remaining, 64);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(mat, stack));
            // Shouldn't happen given the space check, but drop leftovers just in case
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stack;
        }

        // Log
        plugin.getDatabase().logTransaction(player.getUniqueId(), "WITHDRAW", material, amount, null);

        String symbol = plugin.getConfig().getString("currency-symbol", "◆");
        Double configValue = plugin.getCurrencyItems().get(mat);
        double totalValue = configValue != null ? amount * configValue : 0;
        player.sendMessage(MM.deserialize(plugin.msg("withdraw-success")
                .replace("<amount>", BankGUI.formatAmount(totalValue))
                .replace("<symbol>", symbol)));

        // Refresh the open calculator in-place (don't close, don't reset amount)
        double freshBalance = plugin.getDatabase().getItemBalances(player.getUniqueId())
                .getOrDefault(material, 0.0);
        int cur = currentAmount.getOrDefault(player.getUniqueId(), amount);
        // Clamp amount if bank is now insufficient
        long maxAvail = Math.min((long) Math.floor(freshBalance), 64);
        if (cur > maxAvail) {
            cur = (int) maxAvail;
            currentAmount.put(player.getUniqueId(), cur);
        }
        final int finalCur = cur;
        // Use Bukkit scheduler to update after event processing
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory open = player.getOpenInventory().getTopInventory();
            fillCalculator(open, material, freshBalance, finalCur);
        });
    }

    /** Returns true if player's inventory can accept `amount` of `mat` */
    private boolean hasRoomFor(Player player, Material mat, int amount) {
        PlayerInventory inv = player.getInventory();
        int spaceNeeded = amount;
        for (ItemStack slot : inv.getStorageContents()) {
            if (spaceNeeded <= 0) break;
            if (slot == null) {
                spaceNeeded -= Math.min(spaceNeeded, mat.getMaxStackSize());
            } else if (slot.getType() == mat) {
                int free = slot.getMaxStackSize() - slot.getAmount();
                spaceNeeded -= Math.min(spaceNeeded, free);
            }
        }
        return spaceNeeded <= 0;
    }
}
