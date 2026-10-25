package com.autogear.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Парсер legacy-форматирования Minecraft.
 * Поддерживает:
 *   - стандартные коды (&0-&9, &a-&f, &k, &l, &m, &n, &o, &r)
 *   - hex-формат Bungee/Vanilla: &x&R&R&G&G&B&B (каждая пара — один ниббл)
 *
 * Возвращает готовый Component, пригодный для отправки в чат.
 */
public final class LegacyText {

    private LegacyText() {}

    public static Component parse(String input) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;

        int i = 0;
        int len = input.length();

        while (i < len) {
            char c = input.charAt(i);

            if (c == '&' && i + 1 < len) {

                // === HEX: &x&R&R&G&G&B&B — всего 14 символов ===
                if (input.charAt(i + 1) == 'x' && i + 14 <= len) {
                    int r1 = hexVal(input.charAt(i + 3));
                    int r2 = hexVal(input.charAt(i + 5));
                    int g1 = hexVal(input.charAt(i + 7));
                    int g2 = hexVal(input.charAt(i + 9));
                    int b1 = hexVal(input.charAt(i + 11));
                    int b2 = hexVal(input.charAt(i + 13));

                    if (r1 >= 0 && r2 >= 0 && g1 >= 0 && g2 >= 0 && b1 >= 0 && b2 >= 0) {
                        flush(result, buffer, style);

                        int rgb = (r1 << 20) | (r2 << 16) | (g1 << 12) | (g2 << 8) | (b1 << 4) | b2;
                        style = style.withColor(TextColor.fromRgb(rgb));
                        i += 14;
                        continue;
                    }
                }

                // === Стандартный код: &a, &l, &r и т.п. ===
                char code = Character.toLowerCase(input.charAt(i + 1));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    flush(result, buffer, style);

                    if (fmt == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else if (isColor(fmt)) {
                        style = style.withColor(fmt);
                    } else {
                        style = applyFormat(style, fmt);
                    }
                    i += 2;
                    continue;
                }
            }

            buffer.append(c);
            i++;
        }

        flush(result, buffer, style);
        return result;
    }

    private static void flush(MutableComponent result, StringBuilder buffer, Style style) {
        if (buffer.length() > 0) {
            result.append(Component.literal(buffer.toString()).setStyle(style));
            buffer.setLength(0);
        }
    }

    private static boolean isColor(ChatFormatting fmt) {
        return switch (fmt) {
            case BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
                 DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE -> true;
            default -> false;
        };
    }

    private static Style applyFormat(Style style, ChatFormatting fmt) {
        return switch (fmt) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
