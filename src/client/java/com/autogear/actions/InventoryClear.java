package com.autogear.actions;

import com.autogear.chat.Msg;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

/**
 * Очистка инвентаря по клавише Delete.
 *
 * В креативе — сразу creative-пакетами. В survival на разрешённых серверах —
 * через тот же механизм временного креатива, что и загрузка сета
 * (переключение → очистка → возврат режима). Кулдауна нет.
 */
public final class InventoryClear {

    /** Небольшая пауза, чтобы сервер успел применить пакеты перед проверкой. */
    private static final int CONFIRM_TICKS = 2;

    private static boolean active;
    private static int idleTicks;

    private InventoryClear() {}

    public static boolean isBusy() {
        return active;
    }

    /** Запрос на очистку (вызывается по нажатию Delete). */
    public static void request(Minecraft mc) {
        if (mc == null || mc.player == null) return;

        // Пока идёт текущая очистка — молча игнорируем повтор (без кулдауна).
        if (active) return;

        if (TemporaryCreative.isBusy()) {
            Msg.info("Дождитесь завершения операции.");
            return;
        }

        if (mc.player.isCreative()) {
            if (!PacketQueue.isIdle()) {
                Msg.info("Дождитесь завершения текущей операции.");
                return;
            }

            active = true;
            idleTicks = 0;
            GearActions.clearAllSlots();
            Msg.info("Очищаю инвентарь...");
            return;
        }

        // В survival на разрешённых серверах — временный креатив, как при загрузке сета.
        GameType mode = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
        if (mode == GameType.SURVIVAL && AllowedServers.isAllowedServer()) {
            TemporaryCreative.beginClear();
            return;
        }

        Msg.error("Очистка инвентаря доступна только в креативе!");
    }

    /** Вызывается каждый клиентский тик. */
    public static void tick(Minecraft mc) {
        if (!active) return;

        if (mc == null || mc.player == null || mc.getConnection() == null) {
            reset();
            return;
        }

        if (!PacketQueue.isIdle()) {
            idleTicks = 0;
            return;
        }

        idleTicks++;
        if (idleTicks < CONFIRM_TICKS) return;

        if (!mc.player.isCreative()) {
            Msg.error("Игрок уже не в креативе — очистка не подтверждена.");
        } else if (GearActions.isInventoryEmpty()) {
            Msg.ok("Инвентарь очищен.");
        } else {
            Msg.error("Сервер не подтвердил очистку инвентаря.");
        }

        active = false;
    }

    /** Сброс без сообщений (отключение/смена мира). */
    public static void reset() {
        active = false;
        idleTicks = 0;
    }
}
