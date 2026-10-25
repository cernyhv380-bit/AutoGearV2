package com.autogear.actions;

import com.autogear.storage.SetStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;
import java.util.Locale;

/**
 * Определяет, находимся ли мы на сервере, где выполняется временное переключение
 * в креатив для загрузки сета.
 *
 * Список доменов можно переопределить в config.json:
 *   "allowedServers": ["example.com", "play.example.org"]
 *
 * Совпадение — строго точное имя или поддомен проверенного домена.
 * Похожие домены (evil-dexland.org, dexland.org.example.com) не считаются доверенными.
 */
public final class AllowedServers {

    /** Проверенные домены серверов. */
    public static final List<String> DEFAULTS = List.of(
            "hypemc.pro",
            "dexland.org",
            "mineblaze.net",
            "mineblaze.ru"
    );

    private AllowedServers() {}

    /** true, если текущий сервер входит в список разрешённых (в одиночной игре — false). */
    public static boolean isAllowedServer() {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return false;
        return matches(server.ip, hosts());
    }

    /** Хосты из конфига или значения по умолчанию. */
    public static List<String> hosts() {
        List<String> custom = SetStorage.getConfigStringList("allowedServers");
        return custom.isEmpty() ? DEFAULTS : custom;
    }

    /**
     * Чистое правило проверки: адрес (с портом/регистром) разрешён, если после нормализации
     * он совпадает с базой или является её поддоменом.
     */
    public static boolean matches(String address, List<String> bases) {
        String host = normalize(address);
        if (host.isEmpty()) return false;
        for (String base : bases) {
            String b = normalize(base);
            if (b.isEmpty()) continue;
            if (host.equals(b) || host.endsWith("." + b)) return true;
        }
        return false;
    }

    /** Убирает порт, пробелы, завершающую точку и приводит к нижнему регистру. */
    static String normalize(String address) {
        if (address == null) return "";
        String h = address.trim().toLowerCase(Locale.ROOT);
        int colon = h.indexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }
}
