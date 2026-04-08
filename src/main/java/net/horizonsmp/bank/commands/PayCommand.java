package net.horizonsmp.bank.commands;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.gui.BankGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PayCommand implements CommandExecutor {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PayCommand(HorizonBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("horizonbank.use")) {
            player.sendMessage(MM.deserialize(plugin.msg("no-permission")));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<gray>Использование: <white>/pay <игрок> <сумма>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !plugin.getDatabase().accountExists(target.getUniqueId())) {
            player.sendMessage(MM.deserialize(plugin.msg("player-not-found")));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(MM.deserialize("<red>Нельзя переводить самому себе."));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            player.sendMessage(MM.deserialize(plugin.msg("invalid-amount")));
            return true;
        }

        double balance = plugin.getDatabase().getBalance(player.getUniqueId());
        if (balance < amount) {
            player.sendMessage(MM.deserialize(plugin.msg("not-enough-money")));
            return true;
        }

        plugin.getDatabase().setBalance(player.getUniqueId(), balance - amount);
        plugin.getDatabase().setBalance(target.getUniqueId(),
                plugin.getDatabase().getBalance(target.getUniqueId()) + amount);
        plugin.getDatabase().logTransaction(player.getUniqueId(), "TRANSFER_OUT", null, amount, target.getName());
        plugin.getDatabase().logTransaction(target.getUniqueId(), "TRANSFER_IN",  null, amount, player.getName());

        String symbol = plugin.getConfig().getString("currency-symbol", "◆");
        player.sendMessage(MM.deserialize(plugin.msg("transfer-success")
                .replace("<amount>", BankGUI.formatAmount(amount))
                .replace("<symbol>", symbol)
                .replace("<player>", target.getName())));
        target.sendMessage(MM.deserialize(plugin.msg("transfer-received")
                .replace("<amount>", BankGUI.formatAmount(amount))
                .replace("<symbol>", symbol)
                .replace("<player>", player.getName())));
        return true;
    }
}
