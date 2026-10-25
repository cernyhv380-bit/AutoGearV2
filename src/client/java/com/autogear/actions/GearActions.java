package com.autogear.actions;

import com.autogear.chat.Msg;
import com.autogear.storage.SetStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Логика сохранения/загрузки/выбора сетов.
 *
 * Два параллельных канала:
 *   1. Пакет ServerboundSetCreativeModeSlotPacket на сервер (packet slot).
 *   2. Локальное применение (internal slot) — чтобы UI не ждал ответа.
 *
 * Раскладки РАЗНЫЕ:
 *   internal (наш формат): 0-8 хотбар, 9-35 main, 36 ботинки, 37 штаны, 38 нагрудник, 39 шлем, 40 оффхенд
 *   packet (player screen handler): 0 result, 1-4 крафт, 5 шлем, 6 нагрудник, 7 штаны, 8 ботинки, 9-35 main, 36-44 хотбар, 45 оффхенд
 */
public final class GearActions {

    private GearActions() {}

    private static RegistryAccess reg() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        return mc.level.registryAccess();
    }

    // ============================ Индексы ============================

    private static int internalToPacketSlot(int internal) {
        if (internal >= 0 && internal <= 8)   return 36 + internal;
        if (internal >= 9 && internal <= 35)  return internal;
        if (internal == 36) return 8;
        if (internal == 37) return 7;
        if (internal == 38) return 6;
        if (internal == 39) return 5;
        if (internal == 40) return 45;
        return -1;
    }

    /** Локально ставит стак в нужный слот игрока (internal). */
    private static void applyLocal(Minecraft mc, int internal, ItemStack stack) {
        if (mc == null || mc.player == null) return;
        ItemStack copy = (stack == null) ? ItemStack.EMPTY : stack.copy();

        if (internal >= 0 && internal <= 35) {
            mc.player.getInventory().setItem(internal, copy);
        } else if (internal == 36) {
            mc.player.setItemSlot(EquipmentSlot.FEET, copy);
        } else if (internal == 37) {
            mc.player.setItemSlot(EquipmentSlot.LEGS, copy);
        } else if (internal == 38) {
            mc.player.setItemSlot(EquipmentSlot.CHEST, copy);
        } else if (internal == 39) {
            mc.player.setItemSlot(EquipmentSlot.HEAD, copy);
        } else if (internal == 40) {
            mc.player.setItemInHand(InteractionHand.OFF_HAND, copy);
        }
    }

    // ============================ Чтение инвентаря игрока ============================

    public static ItemStack[] readPlayerInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;

        Inventory inv = mc.player.getInventory();
        ItemStack[] out = new ItemStack[SetStorage.SLOT_COUNT];

        for (int i = 0; i < 36; i++) out[i] = inv.getItem(i).copy();
        out[36] = mc.player.getItemBySlot(EquipmentSlot.FEET).copy();
        out[37] = mc.player.getItemBySlot(EquipmentSlot.LEGS).copy();
        out[38] = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        out[39] = mc.player.getItemBySlot(EquipmentSlot.HEAD).copy();
        out[40] = mc.player.getOffhandItem().copy();

        return out;
    }

    // ============================ Очистка ============================

    /** Ставит в очередь очистку всех 41 слота creative-пакетами (без проверки режима). */
    public static void clearAllSlots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        PacketQueue.clear();
        for (int internal = 0; internal < SetStorage.SLOT_COUNT; internal++) {
            final int i = internal;
            final int packetSlot = internalToPacketSlot(i);
            if (packetSlot < 0) continue;

            PacketQueue.enqueue(() -> {
                applyLocal(mc, i, ItemStack.EMPTY);
                PacketQueue.sendCreativeSlot(packetSlot, ItemStack.EMPTY);
            });
        }
    }

    /** Все 41 слот пусты? (по клиентскому состоянию, синхронизированному сервером) */
    public static boolean isInventoryEmpty() {
        ItemStack[] inv = readPlayerInventory();
        if (inv == null) return false;
        for (ItemStack st : inv) {
            if (st != null && !st.isEmpty()) return false;
        }
        return true;
    }

    // ============================ Сохранение ============================

    public static void saveCurrentAsNew() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        RegistryAccess reg = reg();
        if (reg == null) { Msg.error("Не могу сохранить: мир не загружен."); return; }

        int n = SetStorage.nextSetNumber();
        String name = "set_" + n;
        ItemStack[] inv = readPlayerInventory();
        if (inv == null) { Msg.error("Не удалось прочитать инвентарь."); return; }

        String err = SetStorage.saveSetDetailed(name, inv, reg);
        if (err == null) Msg.ok("Сохранён сет " + name + ".");
        else Msg.error("Ошибка сохранения " + name + ": " + err);
    }

    public static void saveCurrentAs(String name) {
        if (name == null || name.isEmpty()) { Msg.error("Укажи имя сета."); return; }

        RegistryAccess reg = reg();
        if (reg == null) { Msg.error("Не могу сохранить: мир не загружен."); return; }

        ItemStack[] inv = readPlayerInventory();
        if (inv == null) { Msg.error("Не удалось прочитать инвентарь."); return; }

        // Если сет с таким именем уже есть (в т.ч. с другим регистром) — перезаписываем его.
        String existing = SetStorage.resolveExistingName(name);
        String target = existing != null ? existing : name;

        String err = SetStorage.saveSetDetailed(target, inv, reg);
        if (err == null) {
            Msg.ok(existing != null
                    ? "Сет " + target + " перезаписан."
                    : "Сохранён сет " + target + ".");
        } else {
            Msg.error("Ошибка сохранения " + target + ": " + err);
        }
    }

    // ============================ Загрузка ============================

    public static void loadActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // Пока идёт временное переключение режима — не запускаем вторую операцию.
        if (TemporaryCreative.isBusy()) {
            Msg.info("Операция уже выполняется, подождите.");
            return;
        }

        String active = SetStorage.getActiveSet();
        if (active == null) {
            Msg.error("Активный сет не выбран. /autogear select <имя>");
            return;
        }

        loadWithMode(active);
    }

    /**
     * Загрузка конкретного сета (из меню). Логика режима та же, что по F4:
     * в креативе — сразу, в survival на разрешённых серверах — временный креатив.
     */
    public static void loadFromMenu(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (name == null || name.isEmpty()) return;

        if (TemporaryCreative.isBusy()) {
            Msg.info("Операция уже выполняется, подождите.");
            return;
        }

        loadWithMode(name);
    }

    /** Общая часть: креатив → сразу, survival на разрешённых серверах → временный креатив. */
    private static void loadWithMode(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (mc.player.isCreative()) {
            loadByName(name, true);
            return;
        }

        // На разрешённых серверах в survival — временно переключаемся в креатив.
        GameType mode = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
        if (mode == GameType.SURVIVAL && AllowedServers.isAllowedServer()) {
            if (!TemporaryCreative.beginLoad(name)) {
                Msg.error("Не удалось начать операцию: нет соединения с сервером.");
            }
            return;
        }

        Msg.error("Autogear доступен только в креативе!");
    }

    public static void loadByName(String name, boolean requireCreative) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (requireCreative && !mc.player.isCreative()) {
            Msg.error("Autogear доступен только в креативе!");
            return;
        }

        RegistryAccess reg = reg();
        if (reg == null) { Msg.error("Не могу загрузить: мир не загружен."); return; }

        ItemStack[] items = SetStorage.loadSet(name, reg);
        if (items == null) {
            Msg.error("Сет '" + name + "' не найден или повреждён.");
            return;
        }

        int nonEmpty = 0;
        for (ItemStack st : items) if (st != null && !st.isEmpty()) nonEmpty++;
        if (nonEmpty == 0) {
            Msg.error("Сет '" + name + "' пустой — загружать нечего.");
            return;
        }

        PacketQueue.clear();
        for (int internal = 0; internal < SetStorage.SLOT_COUNT; internal++) {
            final int i = internal;
            final ItemStack st = (items[internal] == null) ? ItemStack.EMPTY : items[internal];
            final int packetSlot = internalToPacketSlot(i);
            if (packetSlot < 0) continue;

            PacketQueue.enqueue(() -> {
                applyLocal(Minecraft.getInstance(), i, st);
                PacketQueue.sendCreativeSlot(packetSlot, st);
            });
        }

        Msg.info("Загружаю сет " + name + " (" + nonEmpty + " предм.).");
    }

    public static void select(String name) {
        if (name == null || name.isEmpty()) { Msg.error("Укажи имя сета."); return; }
        if (!SetStorage.exists(name)) { Msg.error("Сет '" + name + "' не найден."); return; }
        SetStorage.setActiveSet(name);
        Msg.ok("Активный сет: " + name);
    }

    public static void reloadActive() {
        String active = SetStorage.getActiveSet();
        if (active == null) { Msg.error("Активный сет не выбран."); return; }
        loadByName(active, true);
    }
}
