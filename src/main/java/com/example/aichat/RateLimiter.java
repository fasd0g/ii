package com.example.aichat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private final int perPlayerPer10s;
    private final int globalPer10s;

    private final Map<UUID, Window> perPlayer = new ConcurrentHashMap<>();
    private final Window global = new Window();

    public RateLimiter(int perPlayerPer10s, int globalPer10s) {
        this.perPlayerPer10s = Math.max(1, perPlayerPer10s);
        this.globalPer10s = Math.max(1, globalPer10s);
    }

    public boolean allow(UUID playerId) {
        long now = System.currentTimeMillis();
        Window w = perPlayer.computeIfAbsent(playerId, _ -> new Window());

        synchronized (global) {
            global.roll(now);
            if (global.count >= globalPer10s) return false;
            global.count++;
        }

        synchronized (w) {
            w.roll(now);
            if (w.count >= perPlayerPer10s) return false;
            w.count++;
            return true;
        }
    }

    private static final class Window {
        long startMs = 0;
        int count = 0;

        void roll(long now) {
            if (startMs == 0) startMs = now;
            if (now - startMs >= 10_000) {
                startMs = now;
                count = 0;
            }
        }
    }
}
