AiChat (Paper 1.2.3) — AI-чат бот с динамическими персонами

Сборка:
  gradle build

Готовый JAR:
  build/libs/ai-npc-chat-1.2.3.jar   (fat jar, с зависимостями)

Установка:
  1) Скопируй JAR в server/plugins/
  2) Запусти сервер (создастся plugins/AiChat/config.yml)
  3) Настрой endpoint и ключ:
       /aichat setendpoint https://api.openai.com/v1/chat/completions
       /aichat setkey sk-xxxxxxxx
  4) Проверка:
       /aichat test

Команды:
  /aichat help
  /aichat status
  /aichat reload
  /aichat toggle
  /aichat setname <имя>
  /aichat setchance <0..1>
  /aichat setmention <true|false>
  /aichat setendpoint <url>
  /aichat setkey <apiKey>
  /aichat persona [id]    (для игрока; без id — авто)
  /aichat debug <on|off>
  /aichat test
