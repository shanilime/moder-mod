package com.modid;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AIResponder {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newHttpClient();

    private static final List<JsonObject> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 15;

    // --- ОЧИСТКА ВХОДЯЩЕГО (От мусора сервера) ---
    private static String cleanInput(String rawMessage, String trigger) {
        String text = rawMessage;
        if (trigger != null && !trigger.isEmpty()) {
            text = text.replace(trigger, "");
        }
        text = text.replace("[@]", "");
        text = text.replaceAll("^<[^>]+>\\s*", "").replaceAll("^\\[[^]]+\\]\\s*", "");
        return text.trim();
    }

    // --- ОЧИСТКА ИСХОДЯЩЕГО (ЧТОБЫ НЕ КИКНУЛО) ---
    private static String sanitizeResponse(String aiText) {
        if (aiText == null) return "";

        // 1. Убираем переносы строк (САМАЯ ЧАСТАЯ ПРИЧИНА КИКА)
        // Заменяем их на пробел, чтобы слова не склеились
        String safe = aiText.replace("\n", " ").replace("\r", " ");

        // 2. Убираем знак параграфа (цвета), за это кикает
        safe = safe.replace("§", "");

        // 3. Убираем лишние кавычки
        safe = safe.replace("\"", "");

        // 4. Убираем двойные пробелы, которые могли появиться после удаления \n
        safe = safe.replaceAll("\\s+", " ");

        return safe.trim();
    }

    public static void askAI(String playerMessage, Consumer<String> callback) {
        ModConfig config = ModConfig.getInstance();
        
        if (config.apiKey.isEmpty()) {
            System.err.println("ОШИБКА: API Key не указан в конфиге!");
            return;
        }

        String cleanText = cleanInput(playerMessage, config.triggerPhrase);
        if (cleanText.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", cleanText);

                synchronized (conversationHistory) {
                    conversationHistory.add(userMsg);
                    if (conversationHistory.size() > MAX_HISTORY) {
                        conversationHistory.remove(0);
                    }
                }

                JsonObject jsonBody = new JsonObject();
                jsonBody.addProperty("model", config.model);
                jsonBody.addProperty("temperature", config.temperature);
                jsonBody.addProperty("max_tokens", 300);

                JsonArray messages = new JsonArray();
                
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", config.systemPrompt);
                messages.add(systemMsg);

                synchronized (conversationHistory) {
                    for (JsonObject msg : conversationHistory) {
                        messages.add(msg);
                    }
                }

                jsonBody.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(jsonBody)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                    if (responseJson.has("choices")) {
                        String aiReply = responseJson.getAsJsonArray("choices")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString();
                        
                        // --- ВАЖНО: ЧИСТИМ ОТВЕТ ПЕРЕД ОТПРАВКОЙ ---
                        String safeReply = sanitizeResponse(aiReply);

                        // Также убираем теги, если бот их все-таки сгенерировал
                        safeReply = safeReply.replace("[@]", "").replace(config.triggerPhrase, "");

                        JsonObject botMsg = new JsonObject();
                        botMsg.addProperty("role", "assistant");
                        botMsg.addProperty("content", safeReply);
                        
                        synchronized (conversationHistory) {
                            conversationHistory.add(botMsg);
                        }

                        callback.accept(safeReply);
                    }
                } else {
                    System.err.println("API Error: " + response.body());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void clearHistory() {
        synchronized (conversationHistory) {
            conversationHistory.clear();
            System.out.println("History Cleared");
        }
    }
}