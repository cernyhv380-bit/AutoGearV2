package com.autogear.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Normalizes set data in Minecraft 1.20.1's legacy ItemStack NBT format. */
public final class ReadyNormalizer {

    private static final String[] BANNED = {"minecraft:knockback", "minecraft:thorns"};
    private static final String CURSE = "minecraft:vanishing_curse";
    private static final String[] PARTS = {"boots", "leggings", "chestplate", "helmet"};
    private static final String[] ARMOR_MATERIALS = {
            "netherite_", "diamond_", "iron_", "golden_", "chainmail_", "leather_", "turtle_"
    };
    private static final Map<String, String[]> FALLBACK_TRIM = Map.of(
            "boots", new String[]{"minecraft:redstone", "minecraft:silence"},
            "leggings", new String[]{"minecraft:redstone", "minecraft:silence"},
            "chestplate", new String[]{"minecraft:netherite", "minecraft:silence"},
            "helmet", new String[]{"minecraft:netherite", "minecraft:silence"}
    );

    private ReadyNormalizer() {}

    public static int normalizeAll(boolean vanishingCurse) {
        Map<String, String[]> trim = readTrimFromPvp();
        int changed = 0;
        for (String name : SetStorage.listReadySets()) {
            Path path = SetStorage.resolveSetPath(name);
            if (path != null && normalizeFile(path, trim, vanishingCurse, true)) changed++;
        }
        for (String name : SetStorage.listSets()) {
            Path path = SetStorage.setsDir().resolve(name + ".nbt");
            if (Files.exists(path) && normalizeFile(path, trim, vanishingCurse, false)) changed++;
        }
        return changed;
    }

    private static Map<String, String[]> readTrimFromPvp() {
        Map<String, String[]> out = new HashMap<>();
        Path pvp = SetStorage.setsDir().resolve("pvp.nbt");
        if (Files.exists(pvp)) {
            try {
                CompoundTag root = NbtIo.read(pvp.toFile());
                if (root != null && root.contains("Items")) {
                    ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
                    for (int i = 0; i < items.size(); i++) {
                        if (!(items.get(i) instanceof CompoundTag item) || item.isEmpty()) continue;
                        String part = armorPart(item.getString("id"));
                        if (part == null) continue;
                        CompoundTag trim = item.getCompound("tag").getCompound("Trim");
                        String material = trim.getString("material");
                        String pattern = trim.getString("pattern");
                        if (!material.isEmpty() && !pattern.isEmpty()) out.put(part, new String[]{material, pattern});
                    }
                }
            } catch (IOException ignored) {
                // Fall back to the default trims below.
            }
        }
        for (Map.Entry<String, String[]> entry : FALLBACK_TRIM.entrySet()) {
            out.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static boolean normalizeFile(Path path, Map<String, String[]> trim,
                                         boolean curse, boolean full) {
        CompoundTag root;
        try {
            root = NbtIo.read(path.toFile());
        } catch (IOException e) {
            return false;
        }
        if (root == null || !root.contains("Items")) return false;

        ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof CompoundTag item && !item.isEmpty()
                    && normalizeItem(item, trim, curse, full)) changed = true;
        }
        if (!changed) return false;
        try {
            NbtIo.write(root, path.toFile());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean normalizeItem(CompoundTag item, Map<String, String[]> trimMap,
                                         boolean curse, boolean full) {
        CompoundTag tag = item.getCompound("tag");
        boolean changed = false;

        ListTag enchantments = tag.getList("Enchantments", Tag.TAG_COMPOUND);
        for (int i = enchantments.size() - 1; i >= 0; i--) {
            CompoundTag enchantment = enchantments.getCompound(i);
            String id = enchantment.getString("id");
            if ((full && isBanned(id)) || (!curse && CURSE.equals(id))) {
                enchantments.remove(i);
                changed = true;
            }
        }
        if (curse && !containsEnchantment(enchantments, CURSE)) {
            CompoundTag enchantment = new CompoundTag();
            enchantment.putString("id", CURSE);
            enchantment.putShort("lvl", (short) 1);
            enchantments.add(enchantment);
            changed = true;
        }
        if (changed || tag.contains("Enchantments")) {
            if (enchantments.isEmpty()) tag.remove("Enchantments");
            else tag.put("Enchantments", enchantments);
        }

        if (!full) {
            if (changed) item.put("tag", tag);
            return changed;
        }

        String part = armorPart(item.getString("id"));
        if (part != null) {
            String[] want = trimMap.get(part);
            if (want != null && want.length == 2 && !want[0].isEmpty()) {
                CompoundTag current = tag.getCompound("Trim");
                if (!want[0].equals(current.getString("material"))
                        || !want[1].equals(current.getString("pattern"))) {
                    CompoundTag newTrim = new CompoundTag();
                    newTrim.putString("material", want[0]);
                    newTrim.putString("pattern", want[1]);
                    tag.put("Trim", newTrim);
                    changed = true;
                }
            }
        }

        CompoundTag display = tag.getCompound("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        if (!isBrandLore(lore)) {
            display.put("Lore", SetBranding.brandLoreTag());
            tag.put("display", display);
            changed = true;
        }

        if (changed) item.put("tag", tag);
        return changed;
    }

    private static boolean isBanned(String id) {
        for (String banned : BANNED) if (banned.equals(id)) return true;
        return false;
    }

    private static boolean containsEnchantment(ListTag list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.getCompound(i).getString("id"))) return true;
        }
        return false;
    }

    private static boolean isBrandLore(ListTag lore) {
        if (lore.size() != 3 || !(lore.get(0) instanceof StringTag first)) return false;
        try {
            StringBuilder text = new StringBuilder();
            appendText(JsonParser.parseString(first.getAsString()), text);
            return SetBranding.plainText(0).equals(text.toString());
        } catch (RuntimeException ignored) {
            return false;
        }
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
            for (JsonElement child : extra.getAsJsonArray()) appendText(child, out);
        }
    }

    private static String armorPart(String id) {
        if (id == null || id.isEmpty()) return null;
        String plain = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        for (String part : PARTS) {
            if (!plain.endsWith(part)) continue;
            for (String material : ARMOR_MATERIALS) if (plain.startsWith(material)) return part;
        }
        return null;
    }
}
