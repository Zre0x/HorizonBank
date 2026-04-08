package net.horizonsmp.bank.commands;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.gui.BankGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BalCommand implements CommandExecutor {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BalCommand(HorizonBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("horizonbank.use")) {
            sender.sendMessage(MM.deserialize(plugin.msg("no-permission")));
            return true;
        }

        String symbol = plugin.getConfig().getString("currency-symbol", "◆");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Specify a player name.");
                return true;
            }
            double balance = plugin.getDatabase().getBalance(player.getUniqueId());
            player.sendMessage(MM.deserialize(plugin.msg("balance-self")
                    .replace("<amount>", BankGUI.formatAmount(balance))
                    .replace("<symbol>", symbol)));
            return true;
        }

        // Check other player
        if (!sender.hasPermission("horizonbank.bal.others")) {
            sender.sendMessage(MM.deserialize(plugin.msg("no-permission")));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!plugin.getDatabase().accountExists(target.getUniqueId())) {
            sender.sendMessage(MM.deserialize(plugin.msg("player-not-found")));
            return true;
        }
        if (plugin.getDatabase().isHidden(target.getUniqueId())) {
            sender.sendMessage(MM.deserialize(plugin.msg("balance-hidden")));
            return true;
        }
        double balance = plugin.getDatabase().getBalance(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage(MM.deserialize(plugin.msg("balance-other")
                .replace("<player>", name)
                .replace("<amount>", BankGUI.formatAmount(balance))
                .replace("<symbol>", symbol)));
        return true;
    }
}
