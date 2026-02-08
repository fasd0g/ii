package com.example.aichat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateStore {
    private final Map<UUID, PlayerState> map = new ConcurrentHashMap<>();

    public PlayerState get(UUID id) {
        return map.computeIfAbsent(id, k -> new PlayerState());
    }
}
