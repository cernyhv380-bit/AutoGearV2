package com.autogear.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты нормализатора готовых сетов: запрещённые зачарования, узор брони,
 * тумблер проклятия утраты и идемпотентность.
 */
class ReadyNormalizerTest {

    @TempDir
    Path root;

    @BeforeEach
    void setUp() throws IOException {
        SetStorage.setRoot(root);
        Files.createDirectories(SetStorage.readyDir());
    }

    private void writeSet(String name, CompoundTag... items) throws IOException {
        CompoundTag set = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag item : items) list.add(item);
        set.put("Items", list);
        NbtIo.write(set, SetStorage.readyDir().resolve(name + ".nbt"));
    }

    private static CompoundTag item(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", 1);
        return tag;
    }

    private static CompoundTag withEnchants(String id, String... enchantments) {
        CompoundTag tag = item(id);
        CompoundTag comps = new CompoundTag();
        CompoundTag ench = new CompoundTag();
        for (String e : enchantments) ench.putInt(e, 1);
        comps.put("minecraft:enchantments", ench);
        tag.put("components", comps);
        return tag;
    }

    private CompoundTag componentsOf(String name, int index) throws IOException {
        CompoundTag set = NbtIo.read(SetStorage.readyDir().resolve(name + ".nbt"));
        return set.getListOrEmpty("Items").getCompoundOrEmpty(index)
                .getCompoundOrEmpty("components");
    }

    @Test
    void removesBannedEnchantsAndKeepsOthers() throws IOException {
        writeSet("kit", withEnchants("minecraft:netherite_sword",
                "minecraft:knockback", "minecraft:thorns",
                "minecraft:vanishing_curse", "minecraft:sharpness"));

        assertEquals(1, ReadyNormalizer.normalizeAll(false));

        CompoundTag ench = componentsOf("kit", 0).getCompoundOrEmpty("minecraft:enchantments");
        assertFalse(ench.contains("minecraft:knockback"), "отдача должна быть вырезана");
        assertFalse(ench.contains("minecraft:thorns"), "шипы должны быть вырезаны");
        assertFalse(ench.contains("minecraft:vanishing_curse"), "проклятие должно быть вырезано");
        assertTrue(ench.contains("minecraft:sharpness"), "остальные зачарования не трогаем");
    }

    @Test
    void appliesTrimFromPvpOrFallback() throws IOException {
        writeSet("kit", item("minecraft:netherite_boots"), item("minecraft:netherite_helmet"));

        assertEquals(1, ReadyNormalizer.normalizeAll(false));

        CompoundTag bootsTrim = componentsOf("kit", 0).getCompoundOrEmpty("minecraft:trim");
        assertEquals("minecraft:redstone", bootsTrim.getStringOr("material", ""));
        assertEquals("minecraft:silence", bootsTrim.getStringOr("pattern", ""));

        CompoundTag helmTrim = componentsOf("kit", 1).getCompoundOrEmpty("minecraft:trim");
        assertEquals("minecraft:netherite", helmTrim.getStringOr("material", ""));
        assertEquals("minecraft:silence", helmTrim.getStringOr("pattern", ""));
    }

    @Test
    void usesTrimFromPvpSetWhenPresent() throws IOException {
        Files.createDirectories(SetStorage.setsDir());
        CompoundTag pvp = new CompoundTag();
        ListTag items = new ListTag();
        items.add(withTrimmedArmor("minecraft:netherite_boots",
                "minecraft:emerald", "minecraft:eye"));
        pvp.put("Items", items);
        NbtIo.write(pvp, SetStorage.setsDir().resolve("pvp.nbt"));

        writeSet("kit", item("minecraft:netherite_boots"));
        ReadyNormalizer.normalizeAll(false);

        CompoundTag trim = componentsOf("kit", 0).getCompoundOrEmpty("minecraft:trim");
        assertEquals("minecraft:emerald", trim.getStringOr("material", ""));
        assertEquals("minecraft:eye", trim.getStringOr("pattern", ""));
    }

    private static CompoundTag withTrimmedArmor(String id, String material, String pattern) {
        CompoundTag tag = item(id);
        CompoundTag comps = new CompoundTag();
        CompoundTag trim = new CompoundTag();
        trim.putString("material", material);
        trim.putString("pattern", pattern);
        comps.put("minecraft:trim", trim);
        tag.put("components", comps);
        return tag;
    }

    @Test
    void vanishingCurseToggleAddsAndRemoves() throws IOException {
        writeSet("kit", item("minecraft:netherite_sword"), item("minecraft:totem_of_undying"));

        ReadyNormalizer.normalizeAll(true);
        assertTrue(componentsOf("kit", 0).getCompoundOrEmpty("minecraft:enchantments")
                .contains("minecraft:vanishing_curse"), "тумблер включён — проклятие добавлено");
        assertTrue(componentsOf("kit", 1).getCompoundOrEmpty("minecraft:enchantments")
                .contains("minecraft:vanishing_curse"), "на все предметы, а не только на оружие");

        ReadyNormalizer.normalizeAll(false);
        assertFalse(componentsOf("kit", 0).getCompoundOrEmpty("minecraft:enchantments")
                .contains("minecraft:vanishing_curse"), "тумблер выключен — проклятие убрано");
    }

    @Test
    void secondRunChangesNothing() throws IOException {
        writeSet("kit", withEnchants("minecraft:netherite_chestplate",
                "minecraft:knockback", "minecraft:thorns"));

        assertEquals(1, ReadyNormalizer.normalizeAll(false));
        assertEquals(0, ReadyNormalizer.normalizeAll(false), "повторный прогон не должен ничего менять");
    }

    @Test
    void ignoresEmptySlotsAndFilesWithoutItems() throws IOException {
        writeSet("kit", new CompoundTag(), item("minecraft:shield"));
        CompoundTag broken = new CompoundTag();
        broken.putString("Other", "value");
        NbtIo.write(broken, SetStorage.readyDir().resolve("broken.nbt"));

        assertDoesNotThrow(() -> ReadyNormalizer.normalizeAll(false));
    }

    private static String loreText(CompoundTag comps, int line) {
        ListTag lore = comps.getListOrEmpty("minecraft:lore");
        CompoundTag tag = lore.getCompoundOrEmpty(line);
        StringBuilder sb = new StringBuilder(tag.getStringOr("text", ""));
        ListTag runs = tag.getListOrEmpty("extra");
        for (int i = 0; i < runs.size(); i++) {
            sb.append(runs.getCompoundOrEmpty(i).getStringOr("text", ""));
        }
        return sb.toString();
    }

    @Test
    void replacesForeignLoreWithBranding() throws IOException {
        CompoundTag sword = withEnchants("minecraft:netherite_sword", "minecraft:sharpness");
        CompoundTag comps = sword.getCompoundOrEmpty("components");
        ListTag foreign = new ListTag();
        for (int i = 0; i < 5; i++) {
            CompoundTag line = new CompoundTag();
            line.putString("text", "чужая строка " + i);
            foreign.add(line);
        }
        comps.put("minecraft:lore", foreign);
        writeSet("kit", sword);

        ReadyNormalizer.normalizeAll(false);

        CompoundTag result = componentsOf("kit", 0);
        assertEquals(3, result.getListOrEmpty("minecraft:lore").size(),
                "чужой lore должен быть заменён тремя строками брендинга");
        assertEquals(SetBranding.plainText(0), loreText(result, 0));
        assertEquals(SetBranding.plainText(1), loreText(result, 1));
        assertEquals(SetBranding.plainText(2), loreText(result, 2));
    }

    @Test
    void vanishingCurseTouchesOwnSetsButKeepsTheirEnchants() throws IOException {
        Files.createDirectories(SetStorage.setsDir());
        CompoundTag own = withEnchants("minecraft:netherite_sword",
                "minecraft:thorns", "minecraft:sharpness");
        CompoundTag set = new CompoundTag();
        ListTag list = new ListTag();
        list.add(own);
        set.put("Items", list);
        NbtIo.write(set, SetStorage.setsDir().resolve("mine.nbt"));

        ReadyNormalizer.normalizeAll(true);

        CompoundTag ench = NbtIo.read(SetStorage.setsDir().resolve("mine.nbt"))
                .getListOrEmpty("Items").getCompoundOrEmpty(0)
                .getCompoundOrEmpty("components")
                .getCompoundOrEmpty("minecraft:enchantments");
        assertTrue(ench.contains("minecraft:vanishing_curse"),
                "тумблер проклятия распространяется на свои сеты");
        assertTrue(ench.contains("minecraft:thorns"),
                "остальные правила свои сеты не трогают");
    }
}
