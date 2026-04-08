package net.horizonsmp.bank.commands;

import net.horizonsmp.bank.HorizonBank;
import net.horizonsmp.bank.gui.BaltopGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BaltopCommand implements CommandExecutor {

    private final HorizonBank plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BaltopCommand(HorizonBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("horizonbank.use")) {
            sender.sendMessage(MM.deserialize(plugin.msg("no-permission")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        new BaltopGUI(plugin).open(player);
        return true;
    }
}
