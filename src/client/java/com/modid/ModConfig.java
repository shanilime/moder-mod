package com.modid;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

public class ModConfig {

    private static ModConfig instance;
    private final Path configPath;

    // Поля конфигурации
    public String apiKey = "";
    public String model = "llama-3.1-8b-instant";
    public double temperature = 0.8;
    public String triggerPhrase = "[CHECK]";
    public String systemPrompt = ""; // Будет заполнен по умолчанию ниже

    // Огромный дефолтный промпт (Инструкция + Правила)
    private static final String DEFAULT_PROMPT = """
        Ты — помощник модератора на сервере HolyWorld Minecraft. Ты живой человек.
        Твоя задача — вести диалог с игроком, которого вызвали на проверку на читы.
        Твоя цель — получить ID из программы RustDesk.
        
        ПРАВИЛА ПОВЕДЕНИЯ:
        1. Если игрок предлагает AnyDesk — отказывайся (скажи: "лагает, не работает"). Только RustDesk.
        2. Общайся естественно, кратко. Если игрок хамит — осади его.
        3. Ссылайся на правила сервера ниже.
        
        ПРАВИЛА HOLYWORLD:
        2.4 Запрещённое ПО (Читы, X-Ray). Наказание: Бан 30 дней.
        2.5 Правила проверки: Отказ/Выход/Спор = Бан. Признание до проверки = Бан 20 дней.
        2.5.4 Запрещено очищать корзину/историю перед проверкой.
        """;

    private ModConfig() {
        // Путь к файлу: .minecraft/config/moder-mod.properties
        configPath = FabricLoader.getInstance().getConfigDir().resolve("moder-mod.properties");
        load();
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    public void load() {
        if (!Files.exists(configPath)) {
            saveDefault(); // Создаем файл, если его нет
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);

            this.apiKey = props.getProperty("api_key", "");
            this.model = props.getProperty("model", "llama-3.1-8b-instant");
            
            String tempStr = props.getProperty("temperature", "0.8");
            try { this.temperature = Double.parseDouble(tempStr); } catch (NumberFormatException e) { this.temperature = 0.8; }

            this.triggerPhrase = props.getProperty("trigger_phrase", "[CHECK]");
            
            // Восстанавливаем переносы строк в промпте (в файле они будут как \n)
            String rawPrompt = props.getProperty("system_prompt", DEFAULT_PROMPT);
            this.systemPrompt = rawPrompt.replace("\\n", "\n");

            System.out.println("Mod Config Loaded!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Метод создает файл с нуля с красивыми комментариями
    private void saveDefault() {
        // Превращаем переносы строк в \n, чтобы промпт был в одну строку в файле
        String escapedPrompt = DEFAULT_PROMPT.replace("\n", "\\n");

        String content = """
            # --- MODERATOR AI MOD CONFIG ---
            # Вставьте сюда ваш ключ от Groq (начинается на gsk_...)
            api_key=
            
            # Модель нейросети (рекомендуется llama-3.1-8b-instant или llama-3.3-70b-versatile)
            model=llama-3.1-8b-instant
            
            # Температура (креативность): 0.0 - робот, 1.0 - полный хаос. Оптимально 0.6 - 0.8
            temperature=0.8
            
            # Фраза-триггер. Бот будет отвечать ТОЛЬКО на сообщения, содержащие этот текст.
            # Чтобы бот отвечал на ВСЁ, оставьте поле пустым (после знака =)
            trigger_phrase=[CHECK]
            
            # Системный промпт (Инструкция + Правила).
            # Используйте \\n для переноса строки.
            system_prompt=%s
            """.formatted(escapedPrompt);

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
            writer.write(content);
            System.out.println("Default config created at " + configPath);
            
            // Устанавливаем значения в память
            this.systemPrompt = DEFAULT_PROMPT;
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}