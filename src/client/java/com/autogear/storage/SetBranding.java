package com.autogear.storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Брендинг предметов AutoGear: три строки lore с плавным градиентом,
 * которые получает каждый предмет любого сета.
 *
 * <p>Строки задаются парами «кодпоинт символа + цвет», поэтому ровно совпадают
 * с согласованным JSON независимо от кодировки исходника:
 * <pre>
 * ʙʏ:ɢɪɢᴀʙʏᴛᴇ11
 * ᴛ.ᴍᴇ/ɢɪɢᴀʙʏᴛᴇǫǫ
 * ᴀᴜᴛᴏɢᴇᴀʀᴠ2
 * </pre>
 */
public final class SetBranding {

    /** Строка 1: ʙʏ:ɢɪɢᴀʙʏᴛᴇ11. */
    private static final int[][] LINE_1 = {
            {0x0299, 0xFF0000}, {0x028F, 0xEA0000}, {0x003A, 0xD50000},
            {0x0262, 0xC00000}, {0x026A, 0xAB0000}, {0x0262, 0x960000},
            {0x1D00, 0x810000}, {0x0299, 0x960000}, {0x028F, 0xAB0000},
            {0x1D1B, 0xC00000}, {0x1D07, 0xD50000}, {0x0031, 0xEA0000},
            {0x0031, 0xFF0000},
    };

    /** Строка 2: ᴛ.ᴍᴇ/ɢɪɢᴀʙʏᴛᴇǫǫ. */
    private static final int[][] LINE_2 = {
            {0x1D1B, 0xFF0000}, {0x002E, 0xED0000}, {0x1D0D, 0xDB0000},
            {0x1D07, 0xC90000}, {0x002F, 0xB70000}, {0x0262, 0xA50000},
            {0x026A, 0x930000}, {0x0262, 0x810000}, {0x1D00, 0x930000},
            {0x0299, 0xA50000}, {0x028F, 0xB70000}, {0x1D1B, 0xC90000},
            {0x1D07, 0xDB0000}, {0x01EB, 0xED0000}, {0x01EB, 0xFF0000},
    };

    /** Строка 3: ᴀᴜᴛᴏɢᴇᴀʀᴠ2. */
    private static final int[][] LINE_3 = {
            {0x1D00, 0xFF0000}, {0x1D1C, 0xE60000}, {0x1D1B, 0xCD0000},
            {0x1D0F, 0xB30000}, {0x0262, 0x9A0000}, {0x1D07, 0x810000},
            {0x1D00, 0xA10000}, {0x0280, 0xC00000}, {0x1D20, 0xE00000},
            {0x0032, 0xFF0000},
    };

    private static final int[][] NO_LINE = {};

    private static List<Component> cached;
    private static ListTag cachedLore;

    private SetBranding() {}

    /** Готовые строки брендинга (кэшируются — компоненты неизменяемы). */
    public static List<Component> lines() {
        if (cached == null) {
            cached = List.of(buildLine(LINE_1), buildLine(LINE_2), buildLine(LINE_3));
        }
        return cached;
    }

    /**
     * Брендинг в виде готового NBT-списка — для правки файлов сетов напрямую.
     *
     * <p>Структура ровно та, что пишет сам клиент в {@code minecraft:lore}:
     * компонент с пустым {@code text} и списком {@code extra}, где у каждого
     * символа свой {@code color} и {@code bold}. Кодек компонентов здесь не
     * используется — вне игры он не инициализируется.
     */
    public static ListTag brandLoreTag() {
        if (cachedLore == null) {
            ListTag list = new ListTag();
            for (int[][] runs : new int[][][]{LINE_1, LINE_2, LINE_3}) {
                CompoundTag line = new CompoundTag();
                line.putString("text", "");
                ListTag extra = new ListTag();
                for (int[] run : runs) {
                    CompoundTag piece = new CompoundTag();
                    piece.putString("text", new String(Character.toChars(run[0])));
                    piece.putString("color", String.format("#%06X", run[1]));
                    piece.putBoolean("bold", true);
                    piece.putBoolean("italic", false);
                    piece.putBoolean("underlined", false);
                    piece.putBoolean("strikethrough", false);
                    piece.putBoolean("obfuscated", false);
                    extra.add(piece);
                }
                line.put("extra", extra);
                list.add(line);
            }
            cachedLore = list;
        }
        Tag copy = cachedLore.copy();
        return copy instanceof ListTag list ? list : new ListTag();
    }

    /** Текст брендинга без стиля — для проверки «уже забрендировано». */
    public static String plainText(int line) {
        int[][] runs = switch (line) {
            case 0 -> LINE_1;
            case 1 -> LINE_2;
            case 2 -> LINE_3;
            default -> NO_LINE;
        };
        StringBuilder sb = new StringBuilder();
        for (int[] run : runs) {
            sb.appendCodePoint(run[0]);
        }
        return sb.toString();
    }

    /**
     * Относится ли сет к категории «Готовые». Брендинг ставится только им —
     * сеты игрока («Свои» и созданные по F8) не трогаем.
     */
    public static boolean isReadySet(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("pvp_") || lower.startsWith("ready_") || lower.startsWith("popular_");
    }

    /**
     * Дописывает брендинг в начало lore предмета. Существующие строки
     * сохраняются после брендинга. Повторный вызов ничего не дублирует.
     */
    public static void stamp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()
                && plainText(0).equals(lore.lines().get(0).getString())) {
            return;
        }

        List<Component> out = new ArrayList<>(lines());
        if (lore != null) out.addAll(lore.lines());
        stack.set(DataComponents.LORE, new ItemLore(List.copyOf(out)));
    }

    /** Копия стека с брендингом (исходный стек не меняется). */
    public static ItemStack stamped(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        ItemStack copy = stack.copy();
        stamp(copy);
        return copy;
    }

    private static Component buildLine(int[][] runs) {
        MutableComponent line = Component.empty();
        for (int[] run : runs) {
            line.append(Component.literal(new String(Character.toChars(run[0])))
                    .withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(run[1]))
                            .withBold(true)));
        }
        return line;
    }
}
