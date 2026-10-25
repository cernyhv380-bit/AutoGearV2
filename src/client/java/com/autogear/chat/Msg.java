package com.autogear.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Хелпер для вывода сообщений мода в локальный чат клиента.
 */
public final class Msg {

    public static final String PREFIX =
            "&x&2&2&F&F&0&0&lA" +
                    "&x&3&B&F&F&1&C&lu" +
                    "&x&5&3&F&F&3&9&lt" +
                    "&x&6&C&F&F&5&5&lo" +
                    "&x&8&4&F&F&7&1&lG" +
                    "&x&9&D&F&F&8&E&le" +
                    "&x&B&5&F&F&A&A&la" +
                    "&x&C&E&F&F&C&6&lr" +
                    "&x&E&6&F&F&E&3&l " +
                    "&x&F&F&F&F&F&F&l> ";

    private Msg() {}

    public static void info(String text) {
        send(PREFIX + "&x&F&F&F&F&F&F&l" + text);
    }

    public static void ok(String text) {
        send(PREFIX + "&x&5&5&F&F&5&5&l" + text);
    }

    public static void error(String text) {
        send(PREFIX + "&x&F&F&5&5&5&5&l" + text);
    }

    private static void send(String legacy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component parsed = LegacyText.parse(legacy);
        mc.player.sendSystemMessage(parsed);
    }
}
