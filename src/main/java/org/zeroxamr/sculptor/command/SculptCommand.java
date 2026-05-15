package org.zeroxamr.sculptor.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.zeroxamr.sculptor.session.SculptSession;
import org.zeroxamr.sculptor.session.SessionManager;
import org.zeroxamr.sculptor.terrain.TerrainCarverOld;

public class SculptCommand implements CommandExecutor {

    private final SessionManager sessionManager;
    private final TerrainCarverOld carver;

    public SculptCommand(SessionManager sessionManager, TerrainCarverOld carver) {
        this.sessionManager = sessionManager;
        this.carver = carver;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        SculptSession session = sessionManager.getSession(player);

        switch (args[0].toLowerCase()) {

            case "start", "begin" -> {
                giveControlBlock(player);
            }

            case "carve" -> {
                if (!session.hasPoints()) {
                    player.sendMessage(Component.text(
                            "No selection made. Place at least 2 control points first.",
                            NamedTextColor.RED));
                    return true;
                }
                if (session.isCarved()) {
                    player.sendMessage(Component.text(
                            "Already carved. Add or move a point first.",
                            NamedTextColor.YELLOW));
                    return true;
                }
                carver.carve(player, session);
                player.sendMessage(Component.text(
                        "Carving — style: " + session.getStyleName()
                                + "  shape: " + session.getShapeName(),
                        NamedTextColor.GREEN));
            }

            case "undo" -> {
                if (!session.canUndo()) {
                    player.sendMessage(Component.text(
                            "Nothing to undo.", NamedTextColor.YELLOW));
                    return true;
                }
                carver.applyUndo(player.getWorld(), session.popUndo());
                player.sendMessage(Component.text(
                        "Undo applied.", NamedTextColor.AQUA));
            }

            case "style" -> {
                session.cycleStyle();
                player.sendMessage(Component.text(
                        "Style: " + session.getStyleName(), NamedTextColor.LIGHT_PURPLE));
            }

            case "shape" -> {
                session.cycleShape();
                player.sendMessage(Component.text(
                        "Shape: " + session.getShapeName(), NamedTextColor.LIGHT_PURPLE));
            }

            case "animation" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                            "Usage: /sculpt animation <on|off>", NamedTextColor.YELLOW));
                    return true;
                }
                boolean on = args[1].equalsIgnoreCase("on");
                session.setAnimation(on);
                player.sendMessage(Component.text(
                        "Animation " + (on ? "on" : "off") + ".", NamedTextColor.AQUA));
            }

            case "clear" -> {
                session.clear();
                player.sendMessage(Component.text(
                        "Session cleared.", NamedTextColor.GRAY));
            }

            case "help" -> sendHelp(player);

            default -> player.sendMessage(Component.text(
                    "Unknown subcommand. Type /sculpt for help.", NamedTextColor.RED));
        }

        return true;
    }

    private void giveControlBlock(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.EMERALD_BLOCK
                    && item.hasItemMeta()
                    && item.getItemMeta().hasEnchant(Enchantment.EFFICIENCY)) {
                player.sendMessage(Component.text(
                        "You already have a control block.", NamedTextColor.YELLOW));
                return;
            }
        }
        ItemStack block = new ItemStack(Material.EMERALD_BLOCK);
        var meta = block.getItemMeta();
        meta.displayName(Component.text("✦ Control Block", NamedTextColor.GREEN));
        meta.addEnchant(Enchantment.EFFICIENCY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        block.setItemMeta(meta);
        player.getInventory().addItem(block);
        player.sendMessage(Component.text(
                "Control block given. Right-click to add points, left-click to remove.",
                NamedTextColor.AQUA));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("─── Sculptor ───", NamedTextColor.GOLD));
        player.sendMessage(line("/sculpt start|begin", "gives you the control block"));
        player.sendMessage(line("/sculpt carve",       "carves terrain from control points"));
        player.sendMessage(line("/sculpt undo",        "restores terrain before last carve"));
        player.sendMessage(line("/sculpt style",       "cycle: linear-2d → spline-2d → linear-3d → spline-3d"));
        player.sendMessage(line("/sculpt shape",       "cycle: ridge → tube → surface"));
        player.sendMessage(line("/sculpt animation <on|off>", "toggle animated carving"));
        player.sendMessage(line("/sculpt clear",       "clear all control points"));
        player.sendMessage(line("/sculpt help",        "show this list"));
    }

    private Component line(String cmd, String desc) {
        return Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY));
    }
}