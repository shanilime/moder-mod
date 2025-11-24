package com.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ModermodClient implements ClientModInitializer {

    private boolean isEnabled = false;

    @Override
    public void onInitializeClient() {
        // При запуске игры загружаем (или создаем) конфиг
        ModConfig.getInstance(); 
        System.out.println("AI Moderation Mod Loaded. Config checked.");

        // --- КОМАНДЫ ---
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("aimod")
                .then(ClientCommandManager.literal("start")
                    .executes(context -> {
                        isEnabled = true;
                        // При старте перезагрузим конфиг (вдруг ты его поменял, пока игра была запущена)
                        ModConfig.getInstance().load();
                        context.getSource().sendFeedback(Component.literal("§a[AI] §fЗапущен. Триггер: " + ModConfig.getInstance().triggerPhrase));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("stop")
                    .executes(context -> {
                        isEnabled = false;
                        context.getSource().sendFeedback(Component.literal("§c[AI] §fОстановлен."));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("clear")
                    .executes(context -> {
                        AIResponder.clearHistory();
                        context.getSource().sendFeedback(Component.literal("§e[AI] §fИстория очищена."));
                        return 1;
                    }))
                // Команда для принудительной перезагрузки конфига без рестарта
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        ModConfig.getInstance().load();
                        context.getSource().sendFeedback(Component.literal("§b[AI] §fКонфиг перезагружен с диска!"));
                        return 1;
                    }))
            );
        });

        // --- ПРОСЛУШКА ЧАТА ---
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!isEnabled) return;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            String text = message.getString();
            String myName = mc.player.getName().getString();
            
            // Защита от самого себя
            if (text.contains(myName) || text.startsWith("<" + myName + ">")) {
                return;
            }

            if (text.trim().isEmpty()) return;

            // --- ПРОВЕРКА НА ТРИГГЕР ---
            String trigger = ModConfig.getInstance().triggerPhrase;
            
            // Если триггер задан в конфиге, но сообщения его НЕ содержит — игнорируем
            if (!trigger.isEmpty() && !text.contains(trigger)) {
                return;
            }

            // Отправляем в AI
            AIResponder.askAI(text, (aiAnswer) -> {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.connection.sendChat(aiAnswer);
                    }
                });
            });
        });
    }
}