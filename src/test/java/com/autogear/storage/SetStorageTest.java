package com.autogear.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты независимой от Minecraft логики хранения наборов:
 * каталог сетов, конфиг, переименование/удаление, перезапись файла.
 */
class SetStorageTest {

    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        SetStorage.setRoot(root);
    }

    private void touch(String name) throws IOException {
        Files.createDirectories(SetStorage.setsDir());
        Files.write(SetStorage.setsDir().resolve(name + ".nbt"), new byte[]{1});
    }

    private long countFiles() throws IOException {
        try (Stream<Path> s = Files.list(SetStorage.setsDir())) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void emptyDirectoryHasNoSets() {
        assertTrue(SetStorage.listSets().isEmpty());
        assertFalse(SetStorage.exists("pvp"));
        assertNull(SetStorage.resolveExistingName("pvp"));
    }

    @Test
    void nextSetNumberStartsAtOneAndIncrements() throws IOException {
        assertEquals(1, SetStorage.nextSetNumber());
        touch("set_1");
        touch("set_2");
        assertEquals(3, SetStorage.nextSetNumber());
        touch("pvp"); // не влияет на нумерацию
        assertEquals(3, SetStorage.nextSetNumber());
    }

    @Test
    void listSetsSortedAndWithoutExtension() throws IOException {
        touch("b");
        touch("a");
        assertEquals(java.util.List.of("a", "b"), SetStorage.listSets());
    }

    @Test
    void resolveExistingNameIsCaseInsensitive() throws IOException {
        touch("PvP");
        assertNotNull(SetStorage.resolveExistingName("pvp"));
        assertEquals("pvp", SetStorage.resolveExistingName("pvp").toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void renameMovesFileAndUpdatesActiveSet() throws IOException {
        touch("pvp");
        SetStorage.setActiveSet("pvp");

        assertTrue(SetStorage.renameSet("pvp", "arena"));
        assertFalse(Files.exists(SetStorage.setsDir().resolve("pvp.nbt")));
        assertTrue(Files.exists(SetStorage.setsDir().resolve("arena.nbt")));
        assertEquals("arena", SetStorage.getActiveSet());
    }

    @Test
    void renameToExistingNameFailsAndKeepsBoth() throws IOException {
        touch("a");
        touch("b");
        assertFalse(SetStorage.renameSet("a", "b"));
        assertTrue(Files.exists(SetStorage.setsDir().resolve("a.nbt")));
        assertTrue(Files.exists(SetStorage.setsDir().resolve("b.nbt")));
    }

    @Test
    void deleteRemovesOnlyTarget() throws IOException {
        touch("a");
        touch("b");
        assertTrue(SetStorage.deleteSet("a"));
        assertFalse(SetStorage.exists("a"));
        assertTrue(SetStorage.exists("b"));
    }

    @Test
    void activeSetPersistsInConfigFile() throws IOException {
        SetStorage.setActiveSet("pvp");
        assertTrue(Files.exists(SetStorage.configFile()));
        assertEquals("pvp", SetStorage.getActiveSet());

        SetStorage.setActiveSet(null);
        assertNull(SetStorage.getActiveSet());
    }

    @Test
    void overwriteReplacesContentAndKeepsSingleFile() throws IOException {
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        assertNull(SetStorage.writeSetTag("pvp", first));

        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        assertNull(SetStorage.writeSetTag("pvp", second));

        assertEquals(1, countFiles(), "повторная запись не должна создавать второй файл");
        CompoundTag read = NbtIo.read(SetStorage.setsDir().resolve("pvp.nbt"));
        assertEquals("second", read.getStringOr("Marker", ""));
    }

    @Test
    void overwriteDoesNotAffectOtherSets() throws IOException {
        CompoundTag arena = new CompoundTag();
        arena.putString("Marker", "arena");
        assertNull(SetStorage.writeSetTag("arena", arena));

        CompoundTag pvp1 = new CompoundTag();
        pvp1.putString("Marker", "v1");
        assertNull(SetStorage.writeSetTag("pvp", pvp1));

        CompoundTag pvp2 = new CompoundTag();
        pvp2.putString("Marker", "v2");
        assertNull(SetStorage.writeSetTag("pvp", pvp2));

        assertEquals(2, countFiles());
        assertEquals("arena", NbtIo.read(SetStorage.setsDir().resolve("arena.nbt")).getStringOr("Marker", ""));
        assertEquals("v2", NbtIo.read(SetStorage.setsDir().resolve("pvp.nbt")).getStringOr("Marker", ""));
    }

    @Test
    void repeatedSaveScenarioOverwritesSameSet() throws IOException {
        // Первый /save pvp
        String target1 = SetStorage.resolveExistingName("pvp");
        target1 = target1 != null ? target1 : "pvp";
        CompoundTag v1 = new CompoundTag();
        v1.putString("Marker", "v1");
        assertNull(SetStorage.writeSetTag(target1, v1));

        // Инвентарь изменился, повторный /save pvp
        String target2 = SetStorage.resolveExistingName("pvp");
        assertEquals(target1, target2, "повторный save должен попасть в тот же набор");
        CompoundTag v2 = new CompoundTag();
        v2.putString("Marker", "v2");
        assertNull(SetStorage.writeSetTag(target2, v2));

        assertEquals(1, countFiles());
        assertEquals("v2", NbtIo.read(SetStorage.setsDir().resolve("pvp.nbt")).getStringOr("Marker", ""));
    }

    @Test
    void saveWithDifferentCaseDoesNotCreateDuplicate() throws IOException {
        CompoundTag a = new CompoundTag();
        a.putString("Marker", "a");
        assertNull(SetStorage.writeSetTag("PvP", a));

        String existing = SetStorage.resolveExistingName("pvp");
        assertNotNull(existing);
        CompoundTag b = new CompoundTag();
        b.putString("Marker", "b");
        assertNull(SetStorage.writeSetTag(existing, b));

        assertEquals(1, countFiles(), "регистр не должен создавать второй файл");
    }
}
