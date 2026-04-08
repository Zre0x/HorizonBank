package net.horizonsmp.bank.commands;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.gui.BankGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BankCommand implements CommandExecutor {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BankCommand(HorizonBank plugin) {
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
        new BankGUI(plugin).open(player);
        return true;
    }
}
