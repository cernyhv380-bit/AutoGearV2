package com.autogear.actions;

import com.autogear.chat.Msg;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

/**
 * Временное переключение в креатив для действий, требующих creative-пакетов.
 *
 * Сценарий:
 *   1. Сохраняем текущий режим (стартуем только из survival).
 *   2. /gamemode creative.
 *   3. Ждём, пока клиент увидит CREATIVE (таймаут).
 *   4. Выполняем задачу: загрузку сета или очистку инвентаря.
 *   5. Ждём завершения очереди пакетов и ещё 10 тиков (SETTLE_TICKS).
 *   6. Возвращаем исходный режим: /gamemode <режим> (только если всё ещё Creative).
 *   7. Ждём подтверждения восстановления (таймаут), при неудаче — сообщение без повторных команд.
 *
 * Повторные нажатия игнорируются, пока операция не завершена.
 */
public final class TemporaryCreative {

    /** Таймаут ожидания смены режима (100 тиков = 5 секунд). */
    private static final int TIMEOUT_TICKS = 100;
    /** Таймаут отправки пакетов (200 тиков = 10 секунд). */
    private static final int LOAD_TIMEOUT_TICKS = 200;
    /** Пауза после завершения задачи перед возвратом режима. */
    private static final int SETTLE_TICKS = 10;

    private enum State {
        IDLE,
        WAIT_CREATIVE,
        LOADING,
        SETTLE,
        WAIT_RESTORE
    }

    private enum Task {
        LOAD,
        CLEAR
    }

    private static State state = State.IDLE;
    private static Task task;
    private static int ticks;
    private static String pendingSet;
    private static GameType previousMode;

    private TemporaryCreative() {}

    public static boolean isBusy() {
        return state != State.IDLE;
    }

    /** Загрузка сета через временный креатив. */
    public static boolean beginLoad(String setName) {
        return begin(Task.LOAD, setName, "Временно переключаюсь в креатив для загрузки сета...");
    }

    /** Очистка инвентаря через временный креатив. */
    public static boolean beginClear() {
        return begin(Task.CLEAR, null, "Временно переключаюсь в креатив для очистки инвентаря...");
    }

    private static boolean begin(Task t, String setName, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;

        if (state != State.IDLE) {
            Msg.info("Операция уже выполняется, подождите.");
            return true;
        }

        task = t;
        pendingSet = setName;
        previousMode = gameType(mc);
        ticks = 0;
        state = State.WAIT_CREATIVE;

        Msg.info(message);
        mc.getConnection().sendCommand("gamemode creative");
        return true;
    }

    /** Вызывается каждый клиентский тик. */
    public static void tick(Minecraft mc) {
        if (state == State.IDLE) return;

        // Отключение от сервера или смена мира — команды больше не шлём.
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            reset();
            return;
        }

        ticks++;

        switch (state) {
            case WAIT_CREATIVE -> {
                if (gameType(mc) == GameType.CREATIVE) {
                    ticks = 0;
                    state = State.LOADING;
                    if (task == Task.CLEAR) {
                        GearActions.clearAllSlots();
                    } else {
                        GearActions.loadByName(pendingSet, false);
                    }
                } else if (ticks >= TIMEOUT_TICKS) {
                    Msg.error("Сервер не переключил режим в креатив — операция отменена.");
                    reset();
                }
            }
            case LOADING -> {
                if (PacketQueue.isIdle()) {
                    if (task == Task.CLEAR) {
                        Msg.ok("Инвентарь очищен.");
                    }
                    // Задача отправлена; даём серверу применить пакеты.
                    ticks = 0;
                    state = State.SETTLE;
                } else if (ticks >= LOAD_TIMEOUT_TICKS) {
                    Msg.error("Операция не завершилась вовремя — возвращаю исходный режим.");
                    ticks = 0;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                if (ticks >= SETTLE_TICKS) {
                    if (gameType(mc) != GameType.CREATIVE) {
                        Msg.info("Режим уже не Creative — восстановление не требуется.");
                        reset();
                    } else {
                        restorePrevious(mc);
                    }
                }
            }
            case WAIT_RESTORE -> {
                GameType current = gameType(mc);
                if (current != null && current == previousMode) {
                    Msg.ok("Исходный режим восстановлен.");
                    reset();
                } else if (ticks >= TIMEOUT_TICKS) {
                    String name = previousMode == null ? "survival" : previousMode.getName();
                    Msg.error("Не удалось вернуть исходный режим. Переключитесь вручную: /gamemode " + name);
                    reset();
                }
            }
        }
    }

    /** Полный сброс состояния (используется при отключении и после завершения). */
    public static void reset() {
        state = State.IDLE;
        task = null;
        pendingSet = null;
        previousMode = null;
        ticks = 0;
    }

    private static void restorePrevious(Minecraft mc) {
        ticks = 0;
        state = State.WAIT_RESTORE;
        String name = previousMode == null ? "survival" : previousMode.getName();
        mc.getConnection().sendCommand("gamemode " + name);
    }

    private static GameType gameType(Minecraft mc) {
        return mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
    }
}
