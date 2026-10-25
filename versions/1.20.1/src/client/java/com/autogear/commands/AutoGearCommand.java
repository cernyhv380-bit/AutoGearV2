package com.autogear.commands;

import com.autogear.actions.GearActions;
import com.autogear.chat.Msg;
import com.autogear.storage.SetStorage;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

import java.util.List;

/**
 * Клиентская команда /autogear.
 */
public final class AutoGearCommand {

    private AutoGearCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommandManager.literal("autogear")
                    .then(ClientCommandManager.literal("save")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> { GearActions.saveCurrentAs(StringArgumentType.getString(ctx, "name")); return 1; })))
                    .then(ClientCommandManager.literal("load")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> { GearActions.loadByName(StringArgumentType.getString(ctx, "name"), true); return 1; })))
                    .then(ClientCommandManager.literal("select")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> { GearActions.select(StringArgumentType.getString(ctx, "name")); return 1; })))
                    .then(ClientCommandManager.literal("list")
                            .executes(ctx -> { printList(); return 1; }))
                    .then(ClientCommandManager.literal("delete")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        if (!SetStorage.exists(name)) { Msg.error("Сет '" + name + "' не найден."); return 0; }
                                        if (SetStorage.deleteSet(name)) {
                                            if (name.equals(SetStorage.getActiveSet())) SetStorage.setActiveSet(null);
                                            Msg.ok("Удалён сет '" + name + "'.");
                                        } else Msg.error("Не удалось удалить '" + name + "'.");
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("rename")
                            .then(ClientCommandManager.argument("old", StringArgumentType.word())
                                    .then(ClientCommandManager.argument("new", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String oldName = StringArgumentType.getString(ctx, "old");
                                                String newName = StringArgumentType.getString(ctx, "new");
                                                if (!SetStorage.exists(oldName)) { Msg.error("Сет '" + oldName + "' не найден."); return 0; }
                                                if (SetStorage.exists(newName)) { Msg.error("Сет '" + newName + "' уже есть."); return 0; }
                                                if (SetStorage.renameSet(oldName, newName)) Msg.ok("Переименован: " + oldName + " -> " + newName);
                                                else Msg.error("Не удалось переименовать.");
                                                return 1;
                                            }))))
                    .then(ClientCommandManager.literal("reload")
                            .executes(ctx -> { GearActions.reloadActive(); return 1; }))
                    .executes(ctx -> {
                        Msg.info("Команды: save, load, select, list, delete, rename, reload");
                        return 1;
                    })
            );
        });
    }

    private static void printList() {
        List<String> sets = SetStorage.listSets();
        String active = SetStorage.getActiveSet();
        if (sets.isEmpty()) { Msg.info("Сетов нет. F8 сохранит текущий инвентарь."); return; }
        Msg.info("Сеты (" + sets.size() + "):");
        for (String s : sets) {
            Msg.info("  " + s + (s.equals(active) ? " <- активный" : ""));
        }
    }
}

