package net.horizonsmp.bank;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

public class VaultHook implements Economy {

    private final HorizonBank plugin;

    public VaultHook(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getServicesManager().register(Economy.class, this, plugin, ServicePriority.High);
        plugin.getLogger().info("Registered as Vault economy provider.");
    }

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "HorizonBank"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) {
        String symbol = plugin.getConfig().getString("currency-symbol", "◆");
        return (long) amount + " " + symbol;
    }
    @Override public String currencyNamePlural() { return plugin.getConfig().getString("currency-name", "алм. руда"); }
    @Override public String currencyNameSingular() { return plugin.getConfig().getString("currency-name", "алм. руда"); }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getDatabase().accountExists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getDatabase().getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double balance = getBalance(player);
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        double newBalance = balance - amount;
        plugin.getDatabase().setBalance(player.getUniqueId(), newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double newBalance = getBalance(player) + amount;
        plugin.getDatabase().setBalance(player.getUniqueId(), newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override public boolean createPlayerAccount(OfflinePlayer player) { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return false; }

    // Bank methods — not supported
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return notSupported(); }
    @Override public EconomyResponse bankBalance(String name) { return notSupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse createBank(String name, String playerName) { return notSupported(); }
    @Override public EconomyResponse deleteBank(String name) { return notSupported(); }
    @Override public java.util.List<String> getBanks() { return java.util.Collections.emptyList(); }

    // Deprecated String-based methods
    @Override public boolean hasAccount(String playerName) { return false; }
    @Override public boolean hasAccount(String playerName, String worldName) { return false; }
    @Override public double getBalance(String playerName) { return 0; }
    @Override public double getBalance(String playerName, String world) { return 0; }
    @Override public boolean has(String playerName, double amount) { return false; }
    @Override public boolean has(String playerName, String worldName, double amount) { return false; }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return notSupported(); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return notSupported(); }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return notSupported(); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return notSupported(); }
    @Override public boolean createPlayerAccount(String playerName) { return false; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return false; }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not supported");
    }
}
