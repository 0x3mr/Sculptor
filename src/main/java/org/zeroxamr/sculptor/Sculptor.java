package org.zeroxamr.sculptor;

import org.bukkit.plugin.java.JavaPlugin;
import org.zeroxamr.sculptor.command.SculptCommand;
import org.zeroxamr.sculptor.listener.ControlPointListener;
import org.zeroxamr.sculptor.session.SessionManager;
import org.zeroxamr.sculptor.terrain.TerrainCarverOld;

public class Sculptor extends JavaPlugin {

    @Override
    public void onEnable() {
        SessionManager sessionManager = new SessionManager();
        TerrainCarverOld carver         = new TerrainCarverOld(this);

        getServer().getPluginManager().registerEvents(
                new ControlPointListener(sessionManager), this);

        var cmd = getCommand("sculpt");
        if (cmd != null)
            cmd.setExecutor(new SculptCommand(sessionManager, carver));

        getLogger().info("Sculptor enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sculptor disabled.");
    }
}