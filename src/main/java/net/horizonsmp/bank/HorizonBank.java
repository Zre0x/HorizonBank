package net.horizonsmp.bank;

import net.horizonsmp.bank.api.BankAPI;
import net.horizonsmp.bank.commands.*;
import net.horizonsmp.bank.database.DatabaseManager;
import net.horizonsmp.bank.gui.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HorizonBank extends JavaPlugin implements Listener {

    private DatabaseManager database;
    private BankAPI api;
    private VaultHook vaultHook;
    private final Map<Material, Double> currencyItems = new LinkedHashMap<>();

    // Singleton GUI instances — registered once, reused forever
    private BankGUI bankGUI;
    private DepositGUI depositGUI;
    private WithdrawGUI withdrawGUI;
    private BaltopGUI baltopGUI;
    private HistoryGUI historyGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCurrencyItems();

        database = new DatabaseManager(this);
        try {
            database.connect();
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        api = new BankAPI(this);
        vaultHook = new VaultHook(this);
        vaultHook.register();

        // Register GUI listeners once
        bankGUI = new BankGUI(this);
        depositGUI = new DepositGUI(this);
        withdrawGUI = new WithdrawGUI(this);
        baltopGUI = new BaltopGUI(this);
        historyGUI = new HistoryGUI(this);
        getServer().getPluginManager().registerEvents(bankGUI, this);
        getServer().getPluginManager().registerEvents(depositGUI, this);
        getServer().getPluginManager().registerEvents(withdrawGUI, this);
        getServer().getPluginManager().registerEvents(baltopGUI, this);
        getServer().getPluginManager().registerEvents(historyGUI, this);

        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("bal").setExecutor(new BalCommand(this));
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("HorizonBank enabled.");
    }

    @Override
    public void onDisable() {
        if (database != null) database.disconnect();
        getLogger().info("HorizonBank disabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (!database.accountExists(player.getUniqueId())) {
            database.createAccount(player.getUniqueId(), player.getName());
        } else {
            database.updateName(player.getUniqueId(), player.getName());
        }
    }

    private void loadCurrencyItems() {
        currencyItems.clear();
        List<?> items = getConfig().getList("currency.items");
        if (items == null) return;
        for (Object obj : items) {
            if (!(obj instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) obj;
            String itemName = (String) map.get("item");
            Object valueObj = map.get("value");
            if (itemName == null || valueObj == null) continue;
            try {
                Material mat = Material.valueOf(itemName.toUpperCase());
                double value = Double.parseDouble(valueObj.toString());
                currencyItems.put(mat, value);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Unknown currency item: " + itemName);
            }
        }
    }

    public DatabaseManager getDatabase() { return database; }
    public BankAPI getAPI() { return api; }
    public Map<Material, Double> getCurrencyItems() { return currencyItems; }
    public BankGUI getBankGUI() { return bankGUI; }
    public DepositGUI getDepositGUI() { return depositGUI; }
    public WithdrawGUI getWithdrawGUI() { return withdrawGUI; }
    public BaltopGUI getBaltopGUI() { return baltopGUI; }
    public HistoryGUI getHistoryGUI() { return historyGUI; }

    public String msg(String key) {
        return getConfig().getString("messages." + key, "<red>Message not found: " + key);
    }
}
