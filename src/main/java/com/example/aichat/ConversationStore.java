package com.example.aichat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationStore {

    public record Msg(String role, String content) {}

    private final int maxMessages;
    private final Map<UUID, Deque<Msg>> perPlayer = new ConcurrentHashMap<>();

    public ConversationStore(int maxMessages) {
        this.maxMessages = Math.max(4, maxMessages);
    }

    public void appendUser(UUID playerId, String playerName, String message) {
        append(playerId, new Msg("user", playerName + ": " + message));
    }

    public void appendAssistant(UUID playerId, String message) {
        append(playerId, new Msg("assistant", message));
    }

    private void append(UUID playerId, Msg msg) {
        Deque<Msg> q = perPlayer.computeIfAbsent(playerId, _ -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(msg);
            while (q.size() > maxMessages) q.removeFirst();
        }
    }

    public List<Msg> snapshot(UUID playerId) {
        Deque<Msg> q = perPlayer.get(playerId);
        if (q == null) return List.of();
        synchronized (q) {
            return List.copyOf(q);
        }
    }
}
