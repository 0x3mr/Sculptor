package org.zeroxamr.sculptor.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.zeroxamr.sculptor.session.SculptSession;
import org.zeroxamr.sculptor.session.SessionManager;

public class ControlPointListener implements Listener {

    private final SessionManager sessionManager;

    public ControlPointListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isControlBlock(item)) return;
        event.setCancelled(true);

        SculptSession session = sessionManager.getSession(player);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            var loc = event.getClickedBlock().getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY() + 1;
            int z = loc.getBlockZ();
            session.addPoint(x, y, z);
            player.sendMessage(Component.text(
                    "✔ Control point at (" + x + ", " + y + ", " + z + ")  —  total: "
                            + session.getControlPoints().size(),
                    NamedTextColor.GREEN));

        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            var loc = event.getClickedBlock().getLocation();
            boolean removed = session.removePoint(loc.getBlockX(), loc.getBlockZ());
            player.sendMessage(removed
                    ? Component.text("✖ Control point removed.", NamedTextColor.RED)
                    : Component.text("No control point here.", NamedTextColor.GRAY));
        }
    }

    public static boolean isControlBlock(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD_BLOCK) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasEnchant(Enchantment.EFFICIENCY);
    }
}