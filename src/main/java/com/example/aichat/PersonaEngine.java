package com.example.aichat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PersonaEngine {

    public record Persona(String id, double weight, String prompt) {}

    private final Map<String, Persona> personas = new HashMap<>();
    private final int lockSeconds;
    private final double switchMargin;

    public PersonaEngine(ConfigurationSection root) {
        if (root == null) {
            this.lockSeconds = 180;
            this.switchMargin = 2.0;
            personas.put("buddy", new Persona("buddy", 1.0, "Ты дружелюбный ИИ на Minecraft сервере."));
            return;
        }

        this.lockSeconds = root.getInt("lockSeconds", 180);
        this.switchMargin = root.getDouble("switchMargin", 2.0);

        ConfigurationSection list = root.getConfigurationSection("list");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                double w = list.getDouble(id + ".weight", 1.0);
                String p = list.getString(id + ".prompt", "");
                personas.put(id, new Persona(id, w, p));
            }
        }
        personas.putIfAbsent("buddy", new Persona("buddy", 1.0, "Ты дружелюбный ИИ на Minecraft сервере."));
    }

    public Persona pickPersona(PlayerState st, String userMessage, String eventKind, long nowMs) {
        if (st.forcedPersonaId != null && personas.containsKey(st.forcedPersonaId)) {
            st.currentPersonaId = st.forcedPersonaId;
            return personas.get(st.forcedPersonaId);
        }

        if (nowMs < st.personaLockedUntilMs && personas.containsKey(st.currentPersonaId)) {
            return personas.get(st.currentPersonaId);
        }

        Map<String, Double> score = new HashMap<>();
        for (Persona p : personas.values()) score.put(p.id, p.weight);

        long dt = (st.lastEventAtMs == 0) ? 0 : (nowMs - st.lastEventAtMs);
        if (dt > 0) {
            double decay = Math.min(1.0, dt / 120_000.0);
            st.toxicScore *= (1.0 - 0.6 * decay);
            st.momentum *= (1.0 - 0.4 * decay);
        }

        switch (eventKind) {
            case "death" -> {
                bump(score, "coach", 3.0 + st.deathStreak * 0.5);
                bump(score, "calmmod", 1.0);
                bump(score, "hype", -1.0);
            }
            case "advancement" -> {
                bump(score, "hype", 4.0);
                bump(score, "buddy", 1.0);
                bump(score, "coach", -0.5);
            }
            case "join", "quit" -> {
                bump(score, "buddy", 2.0);
                bump(score, "calmmod", 0.5);
            }
            default -> { }
        }

        if (userMessage != null) {
            String m = userMessage.toLowerCase(Locale.ROOT);

            if (containsHelpIntent(m)) bump(score, "coach", 3.0);

            bump(score, "calmmod", st.toxicScore * 2.5);

            if (st.toxicScore < 1.2) bump(score, "sarcasticlight", 0.6);

            if (m.contains("бесит") || m.contains("ненавижу") || m.contains("тупо") || m.contains("капец")) {
                bump(score, "calmmod", 1.5);
            }
        }

        if (st.momentum > 2.0) bump(score, "hype", Math.min(3.0, st.momentum));
        if (st.momentum < -2.0) bump(score, "coach", Math.min(3.0, -st.momentum));

        String best = st.currentPersonaId;
        double bestScore = score.getOrDefault(best, 0.0);

        for (var e : score.entrySet()) {
            if (e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }

        double cur = score.getOrDefault(st.currentPersonaId, 0.0);
        if (!best.equals(st.currentPersonaId) && bestScore < cur + switchMargin) {
            best = st.currentPersonaId;
        }

        st.currentPersonaId = best;
        st.personaLockedUntilMs = nowMs + lockSeconds * 1000L;

        return personas.getOrDefault(best, personas.get("buddy"));
    }

    public boolean hasPersona(String id) {
        return id != null && personas.containsKey(id);
    }

    private static void bump(Map<String, Double> score, String id, double delta) {
        score.put(id, score.getOrDefault(id, 0.0) + delta);
    }

    private static boolean containsHelpIntent(String m) {
        return m.contains("как") || m.contains("помоги") || m.contains("что делать")
                || m.contains("совет") || m.contains("подскажи") || m.contains("гайд");
    }
}
