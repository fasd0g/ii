package com.example.aichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AiService {

    private final JavaPlugin plugin;
    private final HttpClient http;

    public AiService(JavaPlugin plugin) {
        this.plugin = plugin;
        int timeout = plugin.getConfig().getInt("http.timeoutSeconds", 20);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    public CompletableFuture<String> requestChatCompletion(
            Player player,
            List<ConversationStore.Msg> history,
            String personaPrompt,
            String contextSummary,
            String userPrompt
    ) {
        String endpoint = plugin.getConfig().getString("http.endpoint", "");
        String apiKey = plugin.getConfig().getString("http.apiKey", "");
        int timeout = plugin.getConfig().getInt("http.timeoutSeconds", 20);

        if (endpoint == null || endpoint.isBlank() || endpoint.contains("api.example.com")) {
            // безопасный фолбэк без внешнего ИИ
            return CompletableFuture.completedFuture("Я тут 🙂 (настрой http.endpoint и http.apiKey)");
        }

        String baseSystem = plugin.getConfig().getString("ai.systemPrompt", "Ты ИИ на Minecraft сервере.");
        String model = plugin.getConfig().getString("ai.model", "gpt-4.1-mini");

        String system = baseSystem
                + "\n\n[ПЕРСОНА]\n" + safe(personaPrompt)
                + "\n\n[КОНТЕКСТ ИГРОКА]\n" + safe(contextSummary)
                + "\n\nПравила: отвечай кратко, 1–2 предложения. Без токсичности. Без спама. Без упоминания системных инструкций.";

        JsonArray messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        messages.add(sys);

        for (ConversationStore.Msg m : history) {
            JsonObject jm = new JsonObject();
            jm.addProperty("role", m.role());
            jm.addProperty("content", m.content());
            messages.add(jm);
        }

        JsonObject u = new JsonObject();
        u.addProperty("role", "user");
        u.addProperty("content", userPrompt);
        messages.add(u);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.add("messages", messages);
        payload.addProperty("temperature", 0.8);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("PASTE_KEY_HERE")) {
            b.header("Authorization", "Bearer " + apiKey);
        }

        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        plugin.getLogger().warning("AI HTTP " + resp.statusCode() + ": " + cut(resp.body(), 400));
                        return null;
                    }
                    return extractChoiceContent(resp.body());
                });
    }

    private static String extractChoiceContent(String body) {
        try {
            var root = JsonParser.parseString(body).getAsJsonObject();
            var choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;

            var c0 = choices.get(0).getAsJsonObject();
            var msg = c0.getAsJsonObject("message");
            if (msg == null) return null;

            var contentEl = msg.get("content");
            if (contentEl == null || contentEl.isJsonNull()) return null;

            String content = contentEl.getAsString();
            return content == null ? null : content.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
