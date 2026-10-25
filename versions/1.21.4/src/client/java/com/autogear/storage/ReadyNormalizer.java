package com.autogear.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Приводит сеты к правилам AutoGear.
 *
 * <p>Вызывается при запуске игры и при изменении настроек нормализации, поэтому любой сет,
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

    private static final Gson GSON = new Gson();
    private static final Set<String> TEXT_BOOLEAN_FIELDS = Set.of(
            "bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret"
    );

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
                    ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
                    for (int i = 0; i < items.size(); i++) {
                        if (!(items.get(i) instanceof CompoundTag item) || item.isEmpty()) continue;
                        String part = armorPart(item.getString("id"));
                        if (part == null) continue;
                        CompoundTag comps = item.getCompound("components");
                        CompoundTag trim = comps.getCompound("minecraft:trim");
                        if (trim.isEmpty()) continue;
                        String material = trim.getString("material");
                        String pattern = trim.getString("pattern");
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

        ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
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

        if (normalizeLegacyComponents(comps)) changed = true;
        if (normalizeNestedStacks(comps)) changed = true;

        Tag rawEnch = comps.get("minecraft:enchantments");
        CompoundTag ench = rawEnch instanceof CompoundTag e ? e : null;

        if (ench == null && curse) {
            ench = new CompoundTag();
            CompoundTag levels = new CompoundTag();
            ench.put("levels", levels);
            comps.put("minecraft:enchantments", ench);
            changed = true;
        }

        if (ench != null) {
            CompoundTag levels;
            if (ench.contains("levels", Tag.TAG_COMPOUND)) {
                levels = ench.getCompound("levels");
            } else {
                // 1.21.5+ stores enchantment ids directly; 1.21.4 expects them in "levels".
                levels = new CompoundTag();
                for (String key : ench.getAllKeys()) {
                    if (!"show_in_tooltip".equals(key)) levels.put(key, ench.get(key).copy());
                }
                if (ench.contains("show_in_tooltip")) {
                    levels.put("show_in_tooltip", ench.get("show_in_tooltip").copy());
                }
                ench = new CompoundTag();
                ench.put("levels", levels);
                comps.put("minecraft:enchantments", ench);
                changed = true;
            }

            if (full) {
                for (String banned : BANNED) {
                    String key = "minecraft:" + banned;
                    if (levels.contains(key)) {
                        levels.remove(key);
                        changed = true;
                    }
                }
            }
            String curseKey = "minecraft:" + CURSE;
            if (curse) {
                if (!levels.contains(curseKey)) {
                    levels.putInt(curseKey, 1);
                    changed = true;
                }
            } else if (levels.contains(curseKey)) {
                levels.remove(curseKey);
                changed = true;
            }
            if (levels.isEmpty()) {
                comps.remove("minecraft:enchantments");
                changed = true;
            }
        }

        if (!full) return changed;

        String part = armorPart(item.getString("id"));
        if (part != null) {
            String[] want = trimMap.get(part);
            if (want != null && want.length == 2 && !want[0].isEmpty()) {
                CompoundTag current = comps.getCompound("minecraft:trim");
                boolean same = want[0].equals(current.getString("material"))
                        && want[1].equals(current.getString("pattern"));
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

    /** Уже стоит наш брендинг в формате 1.21.4? Сравниваем текст первой строки. */
    private static boolean isBrandLore(ListTag lore) {
        if (lore.size() != 3) return false;
        if (!(lore.get(0) instanceof StringTag first)) return false;
        try {
            StringBuilder text = new StringBuilder();
            appendText(JsonParser.parseString(first.getAsString()), text);
            return SetBranding.plainText(0).equals(text.toString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Converts item components introduced or reshaped after 1.21.4 back to its NBT schema. */
    private static boolean normalizeLegacyComponents(CompoundTag comps) {
        boolean changed = false;

        Tag rawTooltip = comps.get("minecraft:tooltip_display");
        if (rawTooltip instanceof CompoundTag tooltip) {
            ListTag hidden = tooltip.getList("hidden_components", Tag.TAG_STRING);
            if (tooltip.getBoolean("hide_tooltip")) {
                comps.put("minecraft:hide_tooltip", new CompoundTag());
            }
            for (int i = 0; i < hidden.size(); i++) {
                hideLegacyTooltip(comps, hidden.getString(i));
            }
            comps.remove("minecraft:tooltip_display");
            changed = true;
        }

        Tag rawEnchantments = comps.get("minecraft:enchantments");
        if (rawEnchantments instanceof CompoundTag enchantments
                && !enchantments.contains("levels", Tag.TAG_COMPOUND)) {
            CompoundTag levels = new CompoundTag();
            for (String key : enchantments.getAllKeys()) {
                if (!"show_in_tooltip".equals(key)) levels.put(key, enchantments.get(key).copy());
            }
            CompoundTag legacy = new CompoundTag();
            legacy.put("levels", levels);
            if (enchantments.contains("show_in_tooltip")) {
                legacy.put("show_in_tooltip", enchantments.get("show_in_tooltip").copy());
            }
            comps.put("minecraft:enchantments", legacy);
            changed = true;
        }

        Tag rawColor = comps.get("minecraft:dyed_color");
        if (rawColor instanceof IntTag color) {
            CompoundTag legacy = new CompoundTag();
            legacy.putInt("rgb", color.getAsInt());
            comps.put("minecraft:dyed_color", legacy);
            changed = true;
        }

        Tag rawName = comps.get("minecraft:custom_name");
        if (rawName instanceof CompoundTag name) {
            comps.putString("minecraft:custom_name", GSON.toJson(toJson(name, null)));
            changed = true;
        }

        Tag rawLore = comps.get("minecraft:lore");
        if (rawLore instanceof ListTag lore && !lore.isEmpty()
                && lore.get(0) instanceof CompoundTag) {
            ListTag legacy = new ListTag();
            for (int i = 0; i < lore.size(); i++) {
                Tag line = lore.get(i);
                if (line instanceof StringTag string) legacy.add(string.copy());
                else legacy.add(StringTag.valueOf(GSON.toJson(toJson(line, null))));
            }
            comps.put("minecraft:lore", legacy);
            changed = true;
        }

        return changed;
    }

    private static void hideLegacyTooltip(CompoundTag comps, String component) {
        Tag tag = comps.get(component);
        if ("minecraft:dyed_color".equals(component) && tag instanceof IntTag color) {
            CompoundTag value = new CompoundTag();
            value.putInt("rgb", color.getAsInt());
            value.putBoolean("show_in_tooltip", false);
            comps.put(component, value);
            return;
        }
        if (!(tag instanceof CompoundTag value)) return;
        if ("minecraft:enchantments".equals(component)) {
            value.putBoolean("show_in_tooltip", false);
        } else if ("minecraft:dyed_color".equals(component)
                || "minecraft:trim".equals(component)
                || "minecraft:unbreakable".equals(component)) {
            value.putBoolean("show_in_tooltip", false);
        }
    }

    private static boolean normalizeNestedStacks(Tag tag) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING)
                    && compound.get("components") instanceof CompoundTag components) {
                if (normalizeLegacyComponents(components)) changed = true;
            }
            for (String key : compound.getAllKeys()) {
                if (normalizeNestedStacks(compound.get(key))) changed = true;
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                if (normalizeNestedStacks(list.get(i))) changed = true;
            }
        }
        return changed;
    }

    private static JsonElement toJson(Tag tag, String key) {
        if (tag instanceof CompoundTag compound) {
            JsonObject object = new JsonObject();
            for (String child : compound.getAllKeys()) object.add(child, toJson(compound.get(child), child));
            return object;
        }
        if (tag instanceof ListTag list) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < list.size(); i++) array.add(toJson(list.get(i), null));
            return array;
        }
        if (tag instanceof StringTag string) return new JsonPrimitive(string.getAsString());
        if (tag instanceof ByteTag value && key != null && TEXT_BOOLEAN_FIELDS.contains(key)) {
            return new JsonPrimitive(value.getAsByte() != 0);
        }
        if (tag instanceof NumericTag number) return new JsonPrimitive(number.getAsNumber());
        return new JsonPrimitive(tag.getAsString());
    }

    private static void appendText(JsonElement element, StringBuilder out) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.append(element.getAsString());
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        JsonElement text = object.get("text");
        if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
            out.append(text.getAsString());
        }
        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            JsonArray array = extra.getAsJsonArray();
            for (JsonElement child : array) appendText(child, out);
        }
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


