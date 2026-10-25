package com.autogear.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Приводит сеты к правилам AutoGear.
 *
 * <p>Вызывается при запуске игры и при каждом открытии меню, поэтому любой сет,
 * положенный в {@code AutoGear/ready/} руками (например скачанный из канала),
 * автоматически проходит обработку:
 * <ul>
 *   <li>вырезаются {@code knockback} (отдача) и {@code thorns} (шипы);</li>
 *   <li>весь lore заменяется на три строки брендинга AutoGear;</li>
 *   <li>на броню ставится узор из сета {@code pvp};</li>
 *   <li>{@code vanishing_curse} вырезается, а если включён тумблер — наоборот,
 *       добавляется на каждый предмет.</li>
 * </ul>
 * Свои сеты и {@code set_N} из папки {@code sets/} не перекраиваются — у них
 * меняется только проклятие утраты по тумблеру.
 *
 * <p>Файл перезаписывается только если что-то реально изменилось, поэтому
 * повторный прогон ничего не дублирует.
 */
public final class ReadyNormalizer {

    /** Зачарования, которых не должно быть в готовых сетах. */
    private static final String[] BANNED = {"knockback", "thorns"};

    private static final String CURSE = "vanishing_curse";

    private static final String[] PARTS = {"boots", "leggings", "chestplate", "helmet"};

    private static final String[] ARMOR_MATERIALS = {
            "netherite_", "diamond_", "iron_", "golden_", "chainmail_", "leather_", "turtle_",
    };

    /** Значения на случай, если сета pvp нет под рукой. */
    private static final Map<String, String[]> FALLBACK_TRIM = Map.of(
            "boots", new String[]{"minecraft:redstone", "minecraft:silence"},
            "leggings", new String[]{"minecraft:redstone", "minecraft:silence"},
            "chestplate", new String[]{"minecraft:netherite", "minecraft:silence"},
            "helmet", new String[]{"minecraft:netherite", "minecraft:silence"}
    );

    private ReadyNormalizer() {}

    /**
     * Обрабатывает сеты. Готовые из {@code ready/} получают полный набор правил,
     * свои и {@code set_N} из {@code sets/} — только тумблер проклятия утраты.
     *
     * @return число перезаписанных файлов
     */
    public static int normalizeAll(boolean vanishingCurse) {
        Map<String, String[]> trim = readTrimFromPvp();
        int changed = 0;

        for (String name : SetStorage.listReadySets()) {
            Path path = SetStorage.resolveSetPath(name);
            if (path == null) continue;
            if (normalizeFile(path, trim, vanishingCurse, true)) changed++;
        }

        for (String name : SetStorage.listSets()) {
            Path path = SetStorage.setsDir().resolve(name + ".nbt");
            if (!Files.exists(path)) continue;
            if (normalizeFile(path, trim, vanishingCurse, false)) changed++;
        }
        return changed;
    }

    /**
     * Читает узор брони из сета {@code pvp}. Ключ — часть брони
     * (boots/leggings/chestplate/helmet), значение — {материал, узор}.
     */
    public static Map<String, String[]> readTrimFromPvp() {
        Map<String, String[]> out = new HashMap<>();
        Path pvp = SetStorage.setsDir().resolve("pvp.nbt");
        if (Files.exists(pvp)) {
            try {
                CompoundTag root = NbtIo.read(pvp);
                if (root != null && root.contains("Items")) {
                    ListTag items = root.getListOrEmpty("Items");
                    for (int i = 0; i < items.size(); i++) {
                        if (!(items.get(i) instanceof CompoundTag item) || item.isEmpty()) continue;
                        String part = armorPart(item.getStringOr("id", ""));
                        if (part == null) continue;
                        CompoundTag comps = item.getCompoundOrEmpty("components");
                        CompoundTag trim = comps.getCompoundOrEmpty("minecraft:trim");
                        if (trim.isEmpty()) continue;
                        String material = trim.getStringOr("material", "");
                        String pattern = trim.getStringOr("pattern", "");
                        if (!material.isEmpty() && !pattern.isEmpty()) {
                            out.put(part, new String[]{material, pattern});
                        }
                    }
                }
            } catch (IOException ignored) {
                // нет доступа к файлу — останутся значения по умолчанию
            }
        }
        for (Map.Entry<String, String[]> e : FALLBACK_TRIM.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
        }
        return out;
    }

    private static boolean normalizeFile(Path path, Map<String, String[]> trim,
                                         boolean curse, boolean full) {
        CompoundTag root;
        try {
            root = NbtIo.read(path);
        } catch (IOException e) {
            return false;
        }
        if (root == null || !root.contains("Items")) return false;

        ListTag items = root.getListOrEmpty("Items");
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof CompoundTag item) || item.isEmpty()) continue;
            if (normalizeItem(item, trim, curse, full)) changed = true;
        }
        if (!changed) return false;

        try {
            NbtIo.write(root, path);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static boolean normalizeItem(CompoundTag item, Map<String, String[]> trimMap,
                                        boolean curse, boolean full) {
        boolean changed = false;

        CompoundTag comps;
        Tag raw = item.get("components");
        if (raw instanceof CompoundTag existing) {
            comps = existing;
        } else {
            comps = new CompoundTag();
            item.put("components", comps);
            changed = true;
        }

        Tag rawEnch = comps.get("minecraft:enchantments");
        CompoundTag ench = rawEnch instanceof CompoundTag e ? e : null;

        if (ench == null && curse) {
            ench = new CompoundTag();
            comps.put("minecraft:enchantments", ench);
            changed = true;
        }

        if (ench != null) {
            if (full) {
                for (String banned : BANNED) {
                    String key = "minecraft:" + banned;
                    if (ench.contains(key)) {
                        ench.remove(key);
                        changed = true;
                    }
                }
            }
            String curseKey = "minecraft:" + CURSE;
            if (curse) {
                if (!ench.contains(curseKey)) {
                    ench.putInt(curseKey, 1);
                    changed = true;
                }
            } else if (ench.contains(curseKey)) {
                ench.remove(curseKey);
                changed = true;
            }
            if (ench.isEmpty()) {
                comps.remove("minecraft:enchantments");
                changed = true;
            }
        }

        if (!full) return changed;

        String part = armorPart(item.getStringOr("id", ""));
        if (part != null) {
            String[] want = trimMap.get(part);
            if (want != null && want.length == 2 && !want[0].isEmpty()) {
                CompoundTag current = comps.getCompoundOrEmpty("minecraft:trim");
                boolean same = want[0].equals(current.getStringOr("material", ""))
                        && want[1].equals(current.getStringOr("pattern", ""));
                if (!same) {
                    CompoundTag trim = new CompoundTag();
                    trim.putString("material", want[0]);
                    trim.putString("pattern", want[1]);
                    comps.put("minecraft:trim", trim);
                    changed = true;
                }
            }
        }

        Tag rawLore = comps.get("minecraft:lore");
        if (!(rawLore instanceof ListTag lore) || !isBrandLore(lore)) {
            comps.put("minecraft:lore", SetBranding.brandLoreTag());
            changed = true;
        }
        return changed;
    }

    /** Уже стоит наш брендинг? Сравниваем текст первой строки и общее число строк. */
    private static boolean isBrandLore(ListTag lore) {
        if (lore.size() != 3) return false;
        if (!(lore.get(0) instanceof CompoundTag first)) return false;

        StringBuilder text = new StringBuilder(first.getStringOr("text", ""));
        Tag extra = first.get("extra");
        if (extra instanceof ListTag runs) {
            for (int i = 0; i < runs.size(); i++) {
                if (runs.get(i) instanceof CompoundTag run) {
                    text.append(run.getStringOr("text", ""));
                }
            }
        }
        return SetBranding.plainText(0).equals(text.toString());
    }

    /** Часть брони по id предмета ({@code minecraft:netherite_boots} → {@code boots}). */
    private static String armorPart(String id) {
        if (id == null || id.isEmpty()) return null;
        String plain = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        for (String part : PARTS) {
            if (!plain.endsWith(part)) continue;
            for (String material : ARMOR_MATERIALS) {
                if (plain.startsWith(material)) return part;
            }
        }
        return null;
    }
}
