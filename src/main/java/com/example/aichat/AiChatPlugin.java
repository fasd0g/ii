package com.example.aichat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AiChatPlugin extends JavaPlugin implements Listener {

    private AiService ai;
    private ConversationStore convo;
    private RateLimiter limiter;

    private PlayerStateStore states;
    private PersonaEngine personas;

    private boolean enabled;
    private String botName;
    private String botNamePlain;

    private boolean debugPersona = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        this.convo = new ConversationStore(getConfig().getInt("behavior.maxHistoryMessages", 12));
        this.limiter = new RateLimiter(
                getConfig().getInt("rateLimit.perPlayerPer10s", 2),
                getConfig().getInt("rateLimit.globalPer10s", 10)
        );

        this.states = new PlayerStateStore();
        this.personas = new PersonaEngine(getConfig().getConfigurationSection("personas"));
        this.ai = new AiService(this);

        Bukkit.getPluginManager().registerEvents(this, this);

        var cmd = getCommand("aichat");
        if (cmd != null) {
            var handler = new AiChatCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("AiChat enabled (Paper 1.21.11).");
    }

    public void reloadLocalConfig() {
        this.enabled = getConfig().getBoolean("ai.enabled", true);
        this.botName = getConfig().getString("ai.botName", "Прохожий");
        this.botNamePlain = botName.replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }

    public void reloadAll() {
        reloadConfig();
        reloadLocalConfig();
        this.personas = new PersonaEngine(getConfig().getConfigurationSection("personas"));
        this.ai = new AiService(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled || !getConfig().getBoolean("events.onJoin", true)) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("aichat.use")) return;

        PlayerState st = states.get(p.getUniqueId());
        st.lastEventAtMs = System.currentTimeMillis();
        st.deathStreak = 0;

        String prompt = "Игрок " + p.getName() + " зашёл на сервер. Поздоровайся одной-двумя фразами.";
        requestAndBroadcast(p, st, "join", null, prompt);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!enabled || !getConfig().getBoolean("events.onQuit", true)) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("aichat.use")) return;

        PlayerState st = states.get(p.getUniqueId());
        st.lastEventAtMs = System.currentTimeMillis();

        String prompt = "Игрок " + p.getName() + " вышел с сервера. Коротко попрощайся.";
        requestAndBroadcast(p, st, "quit", null, prompt);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!enabled || !getConfig().getBoolean("events.onDeath", true)) return;

        Player p = e.getEntity();
        if (!p.hasPermission("aichat.use")) return;

        PlayerState st = states.get(p.getUniqueId());
        st.lastEventAtMs = System.currentTimeMillis();
        st.deathStreak++;
        st.momentum -= 1.2;

        String deathMsg = e.deathMessage() == null
                ? (p.getName() + " умер(ла).")
                : PlainTextComponentSerializer.plainText().serialize(e.deathMessage());

        String prompt = "Игрок погиб: " + deathMsg + " Скажи что-то уместное (без токсичности), 1 фраза.";
        requestAndBroadcast(p, st, "death", null, prompt);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!enabled || !getConfig().getBoolean("events.onAdvancement", true)) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("aichat.use")) return;

        PlayerState st = states.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        st.lastEventAtMs = now;
        st.deathStreak = 0;
        st.momentum += 2.0;

        String advKey = e.getAdvancement().getKey().toString();

        // 1) антидубль: то же достижение за последние N секунд — пропускаем
        int dedupSec = getConfig().getInt("events.advDedupSeconds", 10);
        if (dedupSec > 0 && advKey.equals(st.lastAdvKey) && (now - st.lastAdvAtMs) < (dedupSec * 1000L)) {
            return;
        }
        st.lastAdvKey = advKey;
        st.lastAdvAtMs = now;

        // 2) рецепты часто спамят — можно игнорировать
        if (getConfig().getBoolean("events.ignoreRecipeAdvancements", true)) {
            String k = advKey.toLowerCase(Locale.ROOT);
            if (k.contains("recipes/") || k.contains("recipe")) {
                return;
            }
        }

        // 3) Фильтр по display/announce-to-chat (убирает “авто-рецепты” и скрытые штуки)
        AdvancementDisplay display = e.getAdvancement().getDisplay();
        if (getConfig().getBoolean("events.requireDisplayForAdvancements", true) && display == null) {
            return;
        }
        if (display != null && getConfig().getBoolean("events.requireAnnounceToChat", true) && !display.doesAnnounceToChat()) {
            return;
        }

        // 4) Доп. фильтр по фрагментам ключа
        var ignoreList = getConfig().getStringList("events.advancementIgnoreContains");
        if (ignoreList != null && !ignoreList.isEmpty()) {
            String k = advKey.toLowerCase(Locale.ROOT);
            for (String s : ignoreList) {
                if (s == null) continue;
                if (!s.isBlank() && k.contains(s.toLowerCase(Locale.ROOT))) {
                    return;
                }
            }
        }

        st.recentAdv.addLast(advKey);
        while (st.recentAdv.size() > 5) st.recentAdv.removeFirst();

        String prompt = "Игрок " + p.getName() + " получил достижение: " + advKey + ". Поздравь коротко.";
        requestAndBroadcast(p, st, "advancement", null, prompt);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!enabled || !getConfig().getBoolean("behavior.replyToChat", true)) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("aichat.use")) return;

        double chance = getConfig().getDouble("behavior.replyChance", 1.0);
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (msg.isEmpty()) return;

        boolean mentionRequired = getConfig().getBoolean("behavior.mentionRequired", false);
        if (mentionRequired && !msg.toLowerCase(Locale.ROOT).contains(botNamePlain)) return;

        PlayerState st = states.get(p.getUniqueId());
        st.lastEventAtMs = System.currentTimeMillis();

        st.toxicScore += Toxic.simpleScore(msg);
        st.toxicScore = Math.min(5.0, st.toxicScore);

        convo.appendUser(p.getUniqueId(), p.getName(), msg);

        String prompt = "Игрок " + p.getName() + " пишет: \"" + msg + "\". Ответь в чате дружелюбно и кратко.";
        requestAndBroadcast(p, st, "chat", msg, prompt);
    }

    private void requestAndBroadcast(Player contextPlayer, PlayerState st, String eventKind, String userMsg, String prompt) {
        if (!limiter.allow(contextPlayer.getUniqueId())) return;

        long now = System.currentTimeMillis();
        PersonaEngine.Persona persona = personas.pickPersona(st, userMsg, eventKind, now);

        if (debugPersona && contextPlayer.isOnline()) {
            String tag = "§7[persona: " + persona.id() + "]";
            contextPlayer.sendActionBar(Component.text(tag));
        }

        String personaPrompt = persona.prompt();
        String contextSummary = buildPlayerContextSummary(st);

        ai.requestChatCompletion(
                        convo.snapshot(contextPlayer.getUniqueId()),
                        personaPrompt,
                        contextSummary,
                        prompt
                )
                .thenAccept(answer -> {
                    if (answer == null || answer.isBlank()) return;

                    String trimmed = trimToMax(answer, getConfig().getInt("behavior.maxResponseChars", 240));
                    convo.appendAssistant(contextPlayer.getUniqueId(), trimmed);

                    Bukkit.getScheduler().runTask(this, () ->
                            Bukkit.broadcast(Component.text(botName + " §7»§r " + trimmed))
                    );
                })
                .exceptionally(ex -> {
                    getLogger().warning("AI error: " + ex.getMessage());
                    return null;
                });
    }

    private String buildPlayerContextSummary(PlayerState st) {
        String adv = st.recentAdv.isEmpty() ? "нет" : String.join(", ", st.recentAdv);

        return "Смерти подряд: " + st.deathStreak
                + "\nMomentum: " + String.format(Locale.ROOT, "%.1f", st.momentum)
                + "\nToxicScore: " + String.format(Locale.ROOT, "%.1f", st.toxicScore)
                + "\nПоследние достижения: " + adv
                + "\nТекущая персона: " + st.currentPersonaId
                + (st.forcedPersonaId != null ? ("\nПринудительная персона: " + st.forcedPersonaId) : "");
    }

    private static String trimToMax(String s, int max) {
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    // ===== API для команд =====

    public boolean isAiEnabled() {
        return getConfig().getBoolean("ai.enabled", true);
    }

    public void setBotNameAndSave(String newName) {
        getConfig().set("ai.botName", newName);
        saveConfig();
        reloadAll();
    }

    public void setEnabledAndSave(boolean value) {
        getConfig().set("ai.enabled", value);
        saveConfig();
        reloadAll();
    }

    public void setDebugPersona(boolean value) {
        this.debugPersona = value;
    }

    public boolean isDebugPersona() {
        return debugPersona;
    }

    public boolean forcePersonaIfExists(Player player, String personaId) {
        if (!personas.hasPersona(personaId)) return false;
        PlayerState st = states.get(player.getUniqueId());
        st.forcedPersonaId = personaId;
        st.currentPersonaId = personaId;
        return true;
    }

    public void clearForcedPersona(Player player) {
        PlayerState st = states.get(player.getUniqueId());
        st.forcedPersonaId = null;
    }

    public void broadcastTestMessage(String text) {
        Bukkit.broadcast(Component.text(botName + " §7»§r " + text));
    }
}
