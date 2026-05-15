package org.zeroxamr.sculptor.session;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final Map<UUID, SculptSession> sessions = new HashMap<>();

    public SculptSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new SculptSession());
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }
}