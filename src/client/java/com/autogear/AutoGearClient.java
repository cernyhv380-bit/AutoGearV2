package com.autogear;

import com.autogear.actions.KeyBinds;
import com.autogear.actions.KeyTickHandler;
import com.autogear.actions.TemporaryCreative;
import com.autogear.commands.AutoGearCommand;
import com.autogear.storage.SetStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Точка входа клиента.
 */
public class AutoGearClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SetStorage.init();
        KeyBinds.register();
        AutoGearCommand.register();
        ClientTickEvents.END_CLIENT_TICK.register(KeyTickHandler::onEndTick);

        // При отключении/подключении сбрасываем незавершённую операцию без отправки команд.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TemporaryCreative.reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TemporaryCreative.reset());
    }
}
