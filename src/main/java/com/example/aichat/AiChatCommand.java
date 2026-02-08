package com.example.aichat;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AiChatCommand implements CommandExecutor, TabCompleter {

    private final AiChatPlugin plugin;

    public AiChatCommand(AiChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage(color("&cНет прав. Нужно: aichat.admin"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "status" -> sendStatus(sender);

            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(color("&aAiChat: конфиг перезагружен."));
            }

            case "toggle" -> {
                boolean now = !plugin.isAiEnabled();
                plugin.setEnabledAndSave(now);
                sender.sendMessage(color("&aAiChat: ai.enabled = " + now));
            }

            case "setname" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " setname <имя>"));
                    return true;
                }
                String newName = join(args, 1);
                plugin.setBotNameAndSave(newName);
                sender.sendMessage(color("&aAiChat: новое имя бота: &f" + newName));
            }

            case "setchance" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " setchance <0.0-1.0>"));
                    return true;
                }
                try {
                    double v = Double.parseDouble(args[1]);
                    if (v < 0.0 || v > 1.0) {
                        sender.sendMessage(color("&cЗначение должно быть от 0.0 до 1.0"));
                        return true;
                    }
                    plugin.getConfig().set("behavior.replyChance", v);
                    plugin.saveConfig();
                    plugin.reloadAll();
                    sender.sendMessage(color("&aAiChat: replyChance = " + v));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(color("&cЭто не число."));
                }
            }

            case "setmention" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " setmention <true|false>"));
                    return true;
                }
                String v = args[1].toLowerCase(Locale.ROOT);
                if (!v.equals("true") && !v.equals("false")) {
                    sender.sendMessage(color("&cНужно указать true или false"));
                    return true;
                }
                boolean b = Boolean.parseBoolean(v);
                plugin.getConfig().set("behavior.mentionRequired", b);
                plugin.saveConfig();
                plugin.reloadAll();
                sender.sendMessage(color("&aAiChat: mentionRequired = " + b));
            }

            case "setendpoint" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " setendpoint <url>"));
                    return true;
                }
                String url = join(args, 1);
                plugin.getConfig().set("http.endpoint", url);
                plugin.saveConfig();
                plugin.reloadAll();
                sender.sendMessage(color("&aAiChat: endpoint установлен."));
            }

            case "setkey" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " setkey <apiKey>"));
                    return true;
                }
                String key = join(args, 1);
                plugin.getConfig().set("http.apiKey", key);
                plugin.saveConfig();
                plugin.reloadAll();
                sender.sendMessage(color("&aAiChat: API-ключ сохранён."));
            }

            case "persona" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cЭта команда доступна только игроку."));
                    return true;
                }

                if (args.length < 2) {
                    plugin.clearForcedPersona(player);
                    sender.sendMessage(color("&aПринудительная персона отключена. Теперь выбор автоматический."));
                    return true;
                }

                String id = args[1].toLowerCase(Locale.ROOT);
                boolean ok = plugin.forcePersonaIfExists(player, id);
                if (!ok) {
                    sender.sendMessage(color("&cНеизвестная персона: &f" + id));
                    return true;
                }
                sender.sendMessage(color("&aПерсона установлена: &f" + id));
            }

            case "debug" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cИспользование: /" + label + " debug <on|off>"));
                    return true;
                }
                boolean v = args[1].equalsIgnoreCase("on");
                plugin.setDebugPersona(v);
                sender.sendMessage(color("&aAiChat: debug = " + v));
            }

            case "test" -> {
                plugin.broadcastTestMessage("Я на связи. Всё работает.");
                sender.sendMessage(color("&aТестовое сообщение отправлено."));
            }

            default -> sender.sendMessage(color("&cНеизвестная команда. Используй /" + label + " help"));
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&bAiChat &7— команды администратора"));
        sender.sendMessage(color("&7/" + label + " &fstatus &7— показать текущие настройки"));
        sender.sendMessage(color("&7/" + label + " &freload &7— перечитать config.yml"));
        sender.sendMessage(color("&7/" + label + " &ftoggle &7— включить/выключить ИИ"));
        sender.sendMessage(color("&7/" + label + " &fsetname <имя> &7— изменить имя бота"));
        sender.sendMessage(color("&7/" + label + " &fsetchance <0..1> &7— шанс ответа в чате"));
        sender.sendMessage(color("&7/" + label + " &fsetmention <true|false> &7— отвечать только при упоминании"));
        sender.sendMessage(color("&7/" + label + " &fsetendpoint <url> &7— задать endpoint"));
        sender.sendMessage(color("&7/" + label + " &fsetkey <ключ> &7— задать API-ключ"));
        sender.sendMessage(color("&7/" + label + " &fpersona [id] &7— принудительная персона (без id — авто)"));
        sender.sendMessage(color("&7/" + label + " &fdebug <on|off> &7— показывать персону в actionbar"));
        sender.sendMessage(color("&7/" + label + " &ftest &7— тестовое сообщение"));
    }

    private void sendStatus(CommandSender sender) {
        boolean enabled = plugin.getConfig().getBoolean("ai.enabled", true);
        String name = plugin.getConfig().getString("ai.botName", "Прохожий");
        String model = plugin.getConfig().getString("ai.model", "openrouter/auto");

        boolean replyToChat = plugin.getConfig().getBoolean("behavior.replyToChat", true);
        double chance = plugin.getConfig().getDouble("behavior.replyChance", 1.0);
        boolean mention = plugin.getConfig().getBoolean("behavior.mentionRequired", false);

        String endpoint = plugin.getConfig().getString("http.endpoint", "");
        int timeout = plugin.getConfig().getInt("http.timeoutSeconds", 20);

        int lock = plugin.getConfig().getInt("personas.lockSeconds", 180);
        double margin = plugin.getConfig().getDouble("personas.switchMargin", 2.0);

        sender.sendMessage(color("&bAiChat status"));
        sender.sendMessage(color("&7ai.enabled: &f" + enabled));
        sender.sendMessage(color("&7ai.botName: &f" + name));
        sender.sendMessage(color("&7ai.model: &f" + model));
        sender.sendMessage(color("&7behavior.replyToChat: &f" + replyToChat));
        sender.sendMessage(color("&7behavior.replyChance: &f" + String.format(Locale.ROOT, "%.2f", chance)));
        sender.sendMessage(color("&7behavior.mentionRequired: &f" + mention));
        sender.sendMessage(color("&7http.endpoint: &f" + endpoint));
        sender.sendMessage(color("&7http.timeoutSeconds: &f" + timeout));
        sender.sendMessage(color("&7personas.lockSeconds: &f" + lock));
        sender.sendMessage(color("&7personas.switchMargin: &f" + String.format(Locale.ROOT, "%.1f", margin)));
        sender.sendMessage(color("&7debug: &f" + plugin.isDebugPersona()));
    }

    private static String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aichat.admin")) return List.of();

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of(
                    "help","status","reload","toggle",
                    "setname","setchance","setmention",
                    "setendpoint","setkey",
                    "persona","debug","test"
            )) {
                if (s.startsWith(p)) out.add(s);
            }
            return out;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "setmention" -> List.of("true", "false");
                case "debug" -> List.of("on", "off");
                case "setchance" -> List.of("1.0", "0.5", "0.25", "0.1", "0.0");
                case "persona" -> List.of("buddy","coach","hype","calmmod","sarcasticlight");
                case "setendpoint" -> List.of(
                        "https://api.openai.com/v1/chat/completions",
                        "https://openrouter.ai/api/v1/chat/completions"
                );
                default -> List.of();
            };
        }

        return List.of();
    }
}
