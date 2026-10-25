package com.autogear.actions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка правила определения разрешённых серверов (без Minecraft-рантайма).
 */
class AllowedServersTest {

    private static final List<String> BASES = AllowedServers.DEFAULTS;

    @Test
    void normalizeStripsPortCaseSpacesAndTrailingDot() {
        assertEquals("mc.dexland.org", AllowedServers.normalize(" MC.DexLand.org:25565 "));
        assertEquals("dexland.org", AllowedServers.normalize("dexland.org."));
        assertEquals("", AllowedServers.normalize(null));
    }

    @Test
    void exactAndSubdomainAllowed() {
        assertTrue(AllowedServers.matches("dexland.org", BASES));
        assertTrue(AllowedServers.matches("mc.dexland.org:25565", BASES));
        assertTrue(AllowedServers.matches("ru.hypemc.pro", BASES));
        assertTrue(AllowedServers.matches("mineblaze.net:25565", BASES));
        assertTrue(AllowedServers.matches("mc.mineblaze.ru", BASES));
    }

    @Test
    void lookalikeDomainsRejected() {
        assertFalse(AllowedServers.matches("evil-dexland.org", BASES));
        assertFalse(AllowedServers.matches("notdexland.org", BASES));
        assertFalse(AllowedServers.matches("dexland.org.evil.com", BASES));
        assertFalse(AllowedServers.matches("hypemc.pro.ru", BASES));
        assertFalse(AllowedServers.matches("mineblaze.net.evil.com", BASES));
    }

    @Test
    void emptyInputRejected() {
        assertFalse(AllowedServers.matches("", BASES));
        assertFalse(AllowedServers.matches(null, BASES));
        assertFalse(AllowedServers.matches("dexland.org", List.of()));
    }

    @Test
    void customBaseListWorks() {
        assertTrue(AllowedServers.matches("play.example.com", List.of("example.com")));
        assertFalse(AllowedServers.matches("example.com.evil", List.of("example.com")));
    }
}
