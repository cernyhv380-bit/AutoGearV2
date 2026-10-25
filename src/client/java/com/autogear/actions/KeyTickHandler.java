package com.autogear.actions;

import com.autogear.menu.AutoGearMenuScreen;
import net.minecraft.client.Minecraft;

/**
 * Слушает нажатия клавиш мода каждый клиентский тик.
 */
public final class KeyTickHandler {

    private KeyTickHandler() {}

    public static void onEndTick(Minecraft client) {
        if (client == null) return;

        // Завершение временного переключения режима и очистки (в т.ч. при отключении/смене мира).
        TemporaryCreative.tick(client);
        InventoryClear.tick(client);

        if (client.player == null) return;

        // F4 — грузим активный сет
        while (KeyBinds.KEY_LOAD.consumeClick()) {
            GearActions.loadActive();
        }

        // F8 — сохраняем текущий инвентарь как новый сет
        while (KeyBinds.KEY_SAVE.consumeClick()) {
            GearActions.saveCurrentAsNew();
        }

        // Delete — очищаем инвентарь (только в креативе)
        while (KeyBinds.KEY_CLEAR.consumeClick()) {
            InventoryClear.request(client);
        }

        // G — открываем меню
        while (KeyBinds.KEY_MENU.consumeClick()) {
            client.gui.setScreen(new AutoGearMenuScreen());
        }

        PacketQueue.tick(client);
    }
}
