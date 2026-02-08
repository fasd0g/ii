package com.example.aichat;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PlayerState {
    public int deathStreak = 0;
    public double momentum = 0.0;
    public double toxicScore = 0.0;

    public long lastEventAtMs = 0;

    // стабилизация выбора персоны
    public long personaLockedUntilMs = 0;
    public String currentPersonaId = "buddy";

    // принудительная персона (по команде админа, только для себя)
    public String forcedPersonaId = null;

    // антидубль достижений
    public String lastAdvKey = null;
    public long lastAdvAtMs = 0;

    public final Deque<String> recentAdv = new ArrayDeque<>();
}
