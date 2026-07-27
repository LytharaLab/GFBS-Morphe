package org.lytharalab.gfbs.morphe.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.lytharalab.gfbs.morphe.api.MorpheServer;
import org.lytharalab.gfbs.morphe.api.MorpheUiAction;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates one screen plus any number of independent named HUD sessions. */
public final class MorpheSessionManager {
    private static final long SESSION_LIFETIME_NANOS = Duration.ofMinutes(30).toNanos();
    private static final int MAX_ACTIONS_PER_SECOND = 100;
    private static final Map<UUID, ConcurrentHashMap<UUID, Session>> SESSIONS = new ConcurrentHashMap<>();

    private MorpheSessionManager() {
    }

    public static Session open(ServerPlayer player, ResourceLocation document) {
        ConcurrentHashMap<UUID, Session> values = sessions(player);
        values.entrySet().removeIf(entry -> entry.getValue().layerId == null);
        return add(values, document, null);
    }

    public static Session openHud(ServerPlayer player, ResourceLocation layerId, ResourceLocation document) {
        ConcurrentHashMap<UUID, Session> values = sessions(player);
        values.entrySet().removeIf(entry -> layerId.equals(entry.getValue().layerId));
        return add(values, document, layerId);
    }

    public static Session close(ServerPlayer player) {
        if (player == null) return null;
        Map<UUID, Session> values = SESSIONS.get(player.getUUID());
        if (values == null) return null;
        for (Map.Entry<UUID, Session> entry : values.entrySet()) {
            if (entry.getValue().layerId == null && values.remove(entry.getKey(), entry.getValue())) {
                cleanup(player);
                return entry.getValue();
            }
        }
        return null;
    }

    public static Session closeHud(ServerPlayer player, ResourceLocation layerId) {
        if (player == null) return null;
        Map<UUID, Session> values = SESSIONS.get(player.getUUID());
        if (values == null) return null;
        for (Map.Entry<UUID, Session> entry : values.entrySet()) {
            if (layerId.equals(entry.getValue().layerId) && values.remove(entry.getKey(), entry.getValue())) {
                cleanup(player);
                return entry.getValue();
            }
        }
        return null;
    }

    public static void closeAll(ServerPlayer player) {
        if (player != null) SESSIONS.remove(player.getUUID());
    }

    public static boolean closeIfMatches(ServerPlayer player, UUID sessionId, ResourceLocation document) {
        Session session = find(player, sessionId);
        if (session == null || !session.document.equals(document)) return false;
        boolean removed = sessions(player).remove(sessionId, session);
        cleanup(player);
        return removed;
    }

    public static boolean accept(ServerPlayer player, MorpheUiAction action) {
        Session session = find(player, action.sessionId());
        if (session == null || !session.document.equals(action.document()) || session.expired()) {
            if (session != null) {
                sessions(player).remove(action.sessionId(), session);
                cleanup(player);
            }
            return false;
        }
        if (!session.allowAction()) return false;
        MorpheServer.dispatch(player, action);
        return true;
    }

    private static Session add(
        ConcurrentHashMap<UUID, Session> values,
        ResourceLocation document,
        ResourceLocation layerId
    ) {
        Session session = new Session(UUID.randomUUID(), document, layerId, System.nanoTime());
        values.put(session.sessionId, session);
        return session;
    }

    private static Session find(ServerPlayer player, UUID sessionId) {
        if (player == null || sessionId == null) return null;
        Map<UUID, Session> values = SESSIONS.get(player.getUUID());
        return values == null ? null : values.get(sessionId);
    }

    private static ConcurrentHashMap<UUID, Session> sessions(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
    }

    private static void cleanup(ServerPlayer player) {
        Map<UUID, Session> values = SESSIONS.get(player.getUUID());
        if (values != null && values.isEmpty()) SESSIONS.remove(player.getUUID(), values);
    }

    public static final class Session {
        private final UUID sessionId;
        private final ResourceLocation document;
        private final ResourceLocation layerId;
        private long lastActivity;
        private long rateWindowStart;
        private int rateCount;

        private Session(UUID sessionId, ResourceLocation document, ResourceLocation layerId, long now) {
            this.sessionId = sessionId;
            this.document = document;
            this.layerId = layerId;
            lastActivity = now;
            rateWindowStart = now;
        }

        public UUID sessionId() { return sessionId; }
        public ResourceLocation document() { return document; }
        public ResourceLocation layerId() { return layerId; }

        private synchronized boolean expired() {
            return System.nanoTime() - lastActivity > SESSION_LIFETIME_NANOS;
        }

        private synchronized boolean allowAction() {
            long now = System.nanoTime();
            if (now - rateWindowStart >= Duration.ofSeconds(1).toNanos()) {
                rateWindowStart = now;
                rateCount = 0;
            }
            if (++rateCount > MAX_ACTIONS_PER_SECOND) return false;
            lastActivity = now;
            return true;
        }
    }
}
