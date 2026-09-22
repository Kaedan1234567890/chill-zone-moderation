package com.chillzone.sus.detect;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.event.events.GrimSetbackEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import com.chillzone.sus.data.SusStore;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges GrimAC's official API into Chill Zone /sus. Grim remains responsible
 * for detection and setbacks; this class only records staff-facing evidence.
 */
public final class GrimSusBridge {
    private static final long SETBACK_MATCH_WINDOW_MS = 2_500L;
    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private GrimSusBridge() {}

    public static void init(Object owner, SusStore store) {
        GrimAbstractAPI api = GrimAPIProvider.get();
        GrimPlugin plugin = api.getGrimPlugin(owner);
        EventBus bus = api.getEventBus();

        bus.get(FlagEvent.class).onFlag(plugin, (user, check, verbose, cancelled) -> {
            HackType type = classify(check.getCheckName());
            if (type == null) return cancelled;

            long now = System.currentTimeMillis();
            store.recordGrimAttempt(user.getUniqueId(), user.getName(), type == HackType.FLY, now);
            pending.put(user.getUniqueId(), new Pending(type, now));
            return cancelled; // Observe only. Never change Grim's own handling.
        });

        GrimSetbackEvent.Channel setbacks = (GrimSetbackEvent.Channel) bus.get(GrimSetbackEvent.class);
        setbacks.onAnySetback(plugin, (user, timestamp) -> {
            long now = System.currentTimeMillis();
            Pending p = pending.get(user.getUniqueId());
            if (p != null && now - p.timeMs <= SETBACK_MATCH_WINDOW_MS) {
                store.recordGrimBlock(user.getUniqueId(), user.getName(), p.type == HackType.FLY, now);
                pending.remove(user.getUniqueId(), p);
            }
        });

        System.out.println("[Chill Zone SUS] GrimAC integration enabled (Fly + Speed evidence).");
    }

    private static HackType classify(String checkName) {
        if (checkName == null) return null;
        String n = checkName.toLowerCase(Locale.ROOT);

        // Grim's prediction/simulation check is the primary impossible-movement
        // signal used here for flight/movement evidence.
        if (n.contains("simulation")) return HackType.FLY;

        // Timer-family checks are the cleanest Grim signal for speed/timer hacks.
        if (n.contains("timer")) return HackType.SPEED;

        return null;
    }

    private enum HackType { FLY, SPEED }
    private record Pending(HackType type, long timeMs) {}
}
