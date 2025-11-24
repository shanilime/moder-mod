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

    private static String cleanInput(String rawMessage) {
        String text = rawMessage;
        // Удаляем [CHECK] и [@] (они могут быть, а могут и не быть)
        text = text.replace("[CHECK]", "").replace("[@]", "");
        
        // Удаляем никнеймы и префиксы в начале строки (всё до двоеточия или >)
        // Пример: "<Steve> Привет" -> "Привет"
        // Пример: "[L] Steve: Привет" -> "Привет"
        text = text.replaceAll("^.*[:>]\\s*", "");
        
        return text.trim();
    }

    private static String sanitizeResponse(String aiText) {
        if (aiText == null) return "";
        return aiText.replace("\n", " ").replace("\r", " ").replace("§", "").replace("\"", "").trim();
    }

    public static void askAI(String playerMessage, Consumer<String> callback) {
        ModConfig config = ModConfig.getInstance();
        
        if (config.apiKey.isEmpty()) {
            System.err.println("ОШИБКА: Нет ключа!");
            return;
        }

        // Чистим
        String cleanText = cleanInput(playerMessage);
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
                jsonBody.addProperty("max_tokens", 350);

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
                        
                        String safeReply = sanitizeResponse(aiReply);

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