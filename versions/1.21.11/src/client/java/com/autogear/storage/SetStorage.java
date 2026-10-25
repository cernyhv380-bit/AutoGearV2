package com.autogear.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Хранилище пресетов и конфига AutoGear.
 */
public final class SetStorage {

    public static final int SLOT_COUNT = 41;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern SET_NUM = Pattern.compile("^set_(\\d+)$");

    private SetStorage() {}

    // ============================ Пути ============================

    private static Path rootOverride;

    /** Для тестов: подменить корневую папку данных. */
    static void setRoot(Path root) { rootOverride = root; }

    public static Path rootDir() {
        if (rootOverride != null) return rootOverride;
        return FabricLoader.getInstance().getGameDir().resolve("AutoGear");
    }
    public static Path setsDir()    { return rootDir().resolve("sets"); }
    /** Папка готовых сетов: сюда кидают .nbt из канала, подпапки — меты. */
    public static Path readyDir()   { return rootDir().resolve("ready"); }
    public static Path configFile() { return rootDir().resolve("config.json"); }

    /**
     * Готовые сеты: все .nbt внутри {@code ready/}, включая подпапки.
     * Возвращает относительные имена без расширения через «/»: {@code crystal/main},
     * {@code hvh/kit_by_player}. Сортировка по имени.
     */
    public static List<String> listReadySets() {
        Path dir = readyDir();
        if (!Files.exists(dir)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(n -> n.endsWith(".nbt"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .forEach(out::add);
        } catch (IOException ignored) {
            return out;
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * Полный путь к сету по имени: сначала папка {@code sets/},
     * затем {@code ready/} с учётом подпапок.
     */
    public static Path resolveSetPath(String name) {
        if (name == null || name.isEmpty()) return null;
        Path direct = setsDir().resolve(name + ".nbt");
        if (Files.exists(direct)) return direct;
        Path ready = readyDir().resolve(name.replace('/', File.separatorChar) + ".nbt");
        if (Files.exists(ready)) return ready;
        return null;
    }

    public static void init() {
        try {
            Files.createDirectories(setsDir());
            if (!Files.exists(configFile())) writeConfig(new JsonObject());
            installBundledSets();
        } catch (IOException ignored) {}

        // Приводим готовые сеты к правилам: узоры из pvp, вырезанные отдача/шипы/проклятие.
        ReadyNormalizer.normalizeAll(isVanishingCurseEnabled());
    }

    /**
     * Тумблер «проклятие утраты». Включён — {@code vanishing_curse} добавляется
     * на каждый предмет готовых сетов; выключен (по умолчанию) — вырезается.
     */
    public static boolean isVanishingCurseEnabled() {
        JsonObject cfg = readConfig();
        if (!cfg.has("vanishingCurse") || !cfg.get("vanishingCurse").isJsonPrimitive()) return false;
        return cfg.get("vanishingCurse").getAsBoolean();
    }

    public static void setVanishingCurseEnabled(boolean enabled) {
        JsonObject cfg = readConfig();
        cfg.addProperty("vanishingCurse", enabled);
        writeConfig(cfg);
    }

    // ===================== Готовые наборы из ресурсов мода =====================

    /** Готовые сеты, которые мод приносит с собой (в jar: /autogear/ready/<имя>.nbt). */
    private static final String[] BUNDLED_SETS = {
            "crystal", "rocket_mace", "web_crystal", "cpvp", "op", "beast",
    };

    /** Версия набора готовых сетов: при её изменении недостающие файлы доливаются. */
    private static final int BUNDLED_VERSION = 8;

    /**
     * Готовит папку и копирует встроенные сеты. Удалённый вручную сет
     * повторно не устанавливается (факт установки запоминается в конфиге).
     */
    private static void installBundledSets() {
        try {
            Files.createDirectories(readyDir());
        } catch (IOException ignored) {}

        JsonObject cfg = readConfig();
        if (cfg.has("bundledSets") && cfg.get("bundledSets").getAsInt() >= BUNDLED_VERSION) return;

        for (String name : BUNDLED_SETS) {
            Path target = readyDir().resolve(name.replace('/', File.separatorChar) + ".nbt");
            if (Files.exists(target)) continue;
            try (InputStream in = SetStorage.class.getResourceAsStream("/autogear/ready/" + name + ".nbt")) {
                if (in == null) continue;
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
            } catch (IOException ignored) {}
        }

        cfg.addProperty("bundledSets", BUNDLED_VERSION);
        writeConfig(cfg);
    }

    // ============================ Config ============================

    public static String getActiveSet() {
        JsonObject cfg = readConfig();
        if (cfg.has("activeSet") && cfg.get("activeSet").isJsonPrimitive()) {
            String s = cfg.get("activeSet").getAsString();
            return (s == null || s.isEmpty()) ? null : s;
        }
        return null;
    }

    public static void setActiveSet(String name) {
        JsonObject cfg = readConfig();
        if (name == null) cfg.remove("activeSet");
        else cfg.addProperty("activeSet", name);
        writeConfig(cfg);
    }

    public static int getPacketsPerTick() {
        JsonObject cfg = readConfig();
        if (cfg.has("packetsPerTick") && cfg.get("packetsPerTick").isJsonPrimitive()) {
            int n = cfg.get("packetsPerTick").getAsInt();
            return Math.max(1, Math.min(200, n));
        }
        return 41;
    }

    /** Читает список строк из config.json (например, allowedServers). Пусто, если ключа нет. */
    public static List<String> getConfigStringList(String key) {
        JsonObject cfg = readConfig();
        if (!cfg.has(key) || !cfg.get(key).isJsonArray()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (JsonElement el : cfg.getAsJsonArray(key)) {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    private static JsonObject readConfig() {
        Path p = configFile();
        if (!Files.exists(p)) return new JsonObject();
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            if (s.isBlank()) return new JsonObject();
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (Exception e) { return new JsonObject(); }
    }

    private static void writeConfig(JsonObject o) {
        try {
            Files.createDirectories(rootDir());
            Files.writeString(configFile(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    // ============================ Sets: список ============================

    public static List<String> listSets() {
        Path dir = setsDir();
        if (!Files.exists(dir)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".nbt"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) { return Collections.emptyList(); }
        return out;
    }

    public static boolean exists(String name) {
        if (name == null || name.isEmpty()) return false;
        return resolveSetPath(name) != null;
    }

    /**
     * Ищет существующий сет с тем же именем без учёта регистра.
     * Нужен, чтобы повторный /save перезаписывал набор, а не создавал дубликат.
     */
    public static String resolveExistingName(String name) {
        if (name == null || name.isEmpty()) return null;
        if (exists(name)) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String s : listSets()) {
            if (s.toLowerCase(Locale.ROOT).equals(lower)) return s;
        }
        return null;
    }

    public static int nextSetNumber() {
        int max = 0;
        for (String name : listSets()) {
            Matcher m = SET_NUM.matcher(name);
            if (m.matches()) {
                try { int n = Integer.parseInt(m.group(1)); if (n > max) max = n; }
                catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    // ============================ Sets: I/O ============================

    public static String saveSetDetailed(String name, ItemStack[] items, RegistryAccess reg) {
        if (name == null || name.isEmpty()) return "пустое имя";
        if (items == null || items.length != SLOT_COUNT) return "неверная длина";
        if (reg == null) return "нет доступа к реестрам";

        RegistryOps<Tag> ops = reg.createSerializationContext(NbtOps.INSTANCE);

        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        boolean brand = SetBranding.isReadySet(name);

        for (int i = 0; i < items.length; i++) {
            ItemStack st = items[i];
            if (st == null || st.isEmpty()) { list.add(new CompoundTag()); continue; }
            // Брендинг ставится только сетам категории «Готовые»
            // («Свои» и сеты по F8 не трогаем); исходный стек игрока не меняется.
            var res = ItemStack.CODEC.encodeStart(ops,
                    brand ? SetBranding.stamped(st) : st);
            if (res.error().isPresent()) return "слот " + i + ": " + res.error().get().message();
            list.add(res.result().orElseGet(CompoundTag::new));
        }
        root.put("Items", list);

        return writeSetTag(name, root);
    }

    /** Записывает NBT-файл сета (перезаписывает существующий). Возвращает null при успехе. */
    static String writeSetTag(String name, CompoundTag root) {
        try {
            Files.createDirectories(setsDir());
            NbtIo.write(root, setsDir().resolve(name + ".nbt"));
            return null;
        } catch (IOException e) {
            return "IOException: " + e.getMessage();
        }
    }

    public static boolean saveSet(String name, ItemStack[] items, RegistryAccess reg) {
        return saveSetDetailed(name, items, reg) == null;
    }

    public static ItemStack[] loadSet(String name, RegistryAccess reg) {
        if (name == null || name.isEmpty()) return null;
        if (reg == null) return null;
        Path p = resolveSetPath(name);
        if (p == null) return null;

        try {
            CompoundTag root = NbtIo.read(p);
            if (root == null || !root.contains("Items")) return null;

            RegistryOps<Tag> ops = reg.createSerializationContext(NbtOps.INSTANCE);
            ListTag list = root.getListOrEmpty("Items");
            ItemStack[] out = new ItemStack[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (i < list.size()) {
                    Tag el = list.get(i);
                    if (el instanceof CompoundTag c && c.isEmpty()) { out[i] = ItemStack.EMPTY; continue; }
                    var res = ItemStack.CODEC.parse(ops, el);
                    out[i] = res.result().orElse(ItemStack.EMPTY);
                } else out[i] = ItemStack.EMPTY;
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean deleteSet(String name) {
        if (name == null || name.isEmpty()) return false;
        Path p = resolveSetPath(name);
        if (p == null) return false;
        try { return Files.deleteIfExists(p); }
        catch (IOException e) { return false; }
    }

    public static boolean renameSet(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        if (oldName.isEmpty() || newName.isEmpty()) return false;
        Path src = resolveSetPath(oldName);
        if (src == null) return false;
        Path dst = src.getParent().resolve(newName + ".nbt");
        if (Files.exists(dst)) return false;
        try {
            Files.move(src, dst);
            if (oldName.equals(getActiveSet())) setActiveSet(newName);
            return true;
        } catch (IOException e) { return false; }
    }
}
