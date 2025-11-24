package com.modid;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ModermodClient implements ClientModInitializer {

    private boolean isEnabled = false;
    private String targetPlayer = ""; // Ник игрока, которого проверяем
    private String lastAiAnswer = "";

    @Override
    public void onInitializeClient() {
        ModConfig.getInstance(); 

        // --- КОМАНДЫ ---
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("aimod")
                
                // ГЛАВНАЯ КОМАНДА: /aimod check <Ник>
                // Пример: /aimod check Steve
                .then(ClientCommandManager.literal("check")
                    .then(ClientCommandManager.argument("nickname", StringArgumentType.word())
                        .executes(context -> {
                            String nick = StringArgumentType.getString(context, "nickname");
                            
                            // Включаем бота
                            isEnabled = true;
                            targetPlayer = nick;
                            
                            // Очищаем старую историю, так как это новый игрок
                            AIResponder.clearHistory();
                            lastAiAnswer = "";
                            
                            context.getSource().sendFeedback(Component.literal("§a[AI] §fПроверка игрока §e" + nick + " §fначата! Бот активен."));
                            return 1;
                        })))

                .then(ClientCommandManager.literal("stop")
                    .executes(context -> {
                        isEnabled = false;
                        targetPlayer = "";
                        context.getSource().sendFeedback(Component.literal("§c[AI] §fОстановлен."));
                        return 1;
                    }))
                
                // Перезагрузка конфига (если поменял ключ)
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        ModConfig.getInstance().load();
                        context.getSource().sendFeedback(Component.literal("§b[AI] §fКонфиг перезагружен!"));
                        return 1;
                    }))
            );
        });

        // --- ЛОГИКА ЧАТА ---
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            processMessage(message.getString());
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) processMessage(message.getString());
        });
    }

    private void processMessage(String text) {
        // 1. Если выключено или нет цели - молчим
        if (!isEnabled || targetPlayer.isEmpty()) return;
        
        if (text == null || text.trim().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 2. ФИЛЬТР ПО НИКУ (САМОЕ ВАЖНОЕ)
        // Мы проверяем, содержится ли ник цели в сообщении.
        // Обычно формат: "<Steve> привет" или "[G] Steve: привет"
        // Поэтому просто contains(targetPlayer) сработает отлично.
        if (!text.contains(targetPlayer)) {
            return; // Это сообщение не от нашего подозреваемого -> игнор
        }

        // 3. Защита от самого себя (на всякий случай)
        String myName = mc.player.getName().getString();
        if (text.contains(myName) && (text.startsWith(myName) || text.contains("<" + myName + ">"))) {
            return;
        }

        // 4. Защита от эха бота
        if (!lastAiAnswer.isEmpty() && text.contains(lastAiAnswer)) {
            return;
        }

        // Отправляем в AI
        AIResponder.askAI(text, (aiAnswer) -> {
            mc.execute(() -> {
                if (mc.player != null) {
                    lastAiAnswer = aiAnswer;
                    mc.player.connection.sendChat(aiAnswer);
                }
            });
        });
    }
}