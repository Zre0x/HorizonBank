package net.horizonsmp.bank.api;

import net.horizonsmp.bank.HorizonBank;

import java.util.UUID;

/**
 * HorizonBank public API.
 * Use HorizonBank.getAPI() to get an instance.
 */
public class BankAPI {

    private final HorizonBank plugin;

    public BankAPI(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public double getBalance(UUID uuid) {
        return plugin.getDatabase().getBalance(uuid);
    }

    public void setBalance(UUID uuid, double amount) {
        plugin.getDatabase().setBalance(uuid, Math.max(0, amount));
    }

    public void addBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        setBalance(uuid, current + amount);
    }

    public boolean removeBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }
}
