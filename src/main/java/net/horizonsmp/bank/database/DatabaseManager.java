package net.horizonsmp.bank.database;

import net.horizonsmp.bank.HorizonBank;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final HorizonBank plugin;
    private Connection connection;

    public DatabaseManager(HorizonBank plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "bank.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        createTables();
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bank_accounts (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    hidden INTEGER NOT NULL DEFAULT 0
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bank_items (
                    uuid TEXT NOT NULL,
                    material TEXT NOT NULL,
                    amount REAL NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, material)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transaction_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    material TEXT,
                    amount REAL NOT NULL,
                    counterpart TEXT,
                    timestamp INTEGER NOT NULL
                )
            """);
        }
    }

    public void createAccount(UUID uuid, String name) {
        boolean hidden = plugin.getConfig().getBoolean("balance-hidden-by-default", false);
        String sql = "INSERT OR IGNORE INTO bank_accounts (uuid, name, hidden) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, hidden ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("createAccount error: " + e.getMessage());
        }
    }

    public void updateName(UUID uuid, String name) {
        String sql = "UPDATE bank_accounts SET name = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("updateName error: " + e.getMessage());
        }
    }

    /** Total balance across all item types (weighted by config value) */
    public double getBalance(UUID uuid) {
        double total = 0;
        Map<String, Double> items = getItemBalances(uuid);
        for (Map.Entry<String, Double> entry : items.entrySet()) {
            try {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(entry.getKey());
                Double value = plugin.getCurrencyItems().get(mat);
                if (value != null) total += entry.getValue() * value;
            } catch (IllegalArgumentException ignored) {}
        }
        return total;
    }

    /** Get stored amounts per material for this player */
    public Map<String, Double> getItemBalances(UUID uuid) {
        Map<String, Double> result = new LinkedHashMap<>();
        String sql = "SELECT material, amount FROM bank_items WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("material"), rs.getDouble("amount"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getItemBalances error: " + e.getMessage());
        }
        return result;
    }

    public void addItemBalance(UUID uuid, String material, double amount) {
        String sql = "INSERT INTO bank_items (uuid, material, amount) VALUES (?, ?, ?) " +
                     "ON CONFLICT(uuid, material) DO UPDATE SET amount = amount + excluded.amount";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, material);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("addItemBalance error: " + e.getMessage());
        }
    }

    public void setItemBalance(UUID uuid, String material, double amount) {
        if (amount <= 0) {
            String sql = "DELETE FROM bank_items WHERE uuid = ? AND material = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, material);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("setItemBalance(delete) error: " + e.getMessage());
            }
            return;
        }
        String sql = "INSERT INTO bank_items (uuid, material, amount) VALUES (?, ?, ?) " +
                     "ON CONFLICT(uuid, material) DO UPDATE SET amount = excluded.amount";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, material);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("setItemBalance error: " + e.getMessage());
        }
    }

    /** Used by Vault — sets total balance by adjusting the primary currency item */
    public void setBalance(UUID uuid, double amount) {
        // Clear all items and re-set using primary currency
        String clearSql = "DELETE FROM bank_items WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(clearSql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("setBalance clear error: " + e.getMessage());
        }
        if (amount > 0) {
            Map.Entry<org.bukkit.Material, Double> primary = plugin.getCurrencyItems()
                    .entrySet().stream().findFirst().orElse(null);
            if (primary != null) {
                setItemBalance(uuid, primary.getKey().name(), amount / primary.getValue());
            }
        }
    }

    public boolean isHidden(UUID uuid) {
        String sql = "SELECT hidden FROM bank_accounts WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("hidden") == 1;
        } catch (SQLException e) {
            plugin.getLogger().warning("isHidden error: " + e.getMessage());
        }
        return false;
    }

    public void setHidden(UUID uuid, boolean hidden) {
        String sql = "UPDATE bank_accounts SET hidden = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("setHidden error: " + e.getMessage());
        }
    }

    public boolean accountExists(UUID uuid) {
        String sql = "SELECT 1 FROM bank_accounts WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().warning("accountExists error: " + e.getMessage());
        }
        return false;
    }

    public void logTransaction(java.util.UUID uuid, String type, String material, double amount, String counterpart) {
        String sql = "INSERT INTO transaction_log (uuid, type, material, amount, counterpart, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.setString(3, material);
            ps.setDouble(4, amount);
            ps.setString(5, counterpart);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("logTransaction error: " + e.getMessage());
        }
    }

    /** Returns transactions ordered newest-first. limit=0 means all. */
    public List<Transaction> getTransactions(java.util.UUID uuid, int limit) {
        List<Transaction> result = new ArrayList<>();
        String sql = "SELECT type, material, amount, counterpart, timestamp FROM transaction_log WHERE uuid = ? ORDER BY timestamp DESC"
                + (limit > 0 ? " LIMIT " + limit : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new Transaction(
                        rs.getString("type"), rs.getString("material"),
                        rs.getDouble("amount"), rs.getString("counterpart"),
                        rs.getLong("timestamp")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getTransactions error: " + e.getMessage());
        }
        return result;
    }

    public static class Transaction {
        public final String type;
        public final String material;
        public final double amount;
        public final String counterpart;
        public final long timestamp;
        public Transaction(String type, String material, double amount, String counterpart, long timestamp) {
            this.type = type; this.material = material; this.amount = amount;
            this.counterpart = counterpart; this.timestamp = timestamp;
        }
    }

    public static class TopEntry {
        public final String name;
        public final double total;
        public final boolean hidden;
        public TopEntry(String name, double total, boolean hidden) {
            this.name = name; this.total = total; this.hidden = hidden;
        }
    }

    public List<TopEntry> getTopBalances(int limit) {
        List<TopEntry> result = new ArrayList<>();
        String sql = "SELECT a.name, a.hidden, COALESCE(SUM(i.amount), 0) as total " +
                     "FROM bank_accounts a LEFT JOIN bank_items i ON a.uuid = i.uuid " +
                     "GROUP BY a.uuid, a.name, a.hidden ORDER BY total DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new TopEntry(
                        rs.getString("name"),
                        rs.getDouble("total"),
                        rs.getInt("hidden") == 1));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getTopBalances error: " + e.getMessage());
        }
        return result;
    }
}
