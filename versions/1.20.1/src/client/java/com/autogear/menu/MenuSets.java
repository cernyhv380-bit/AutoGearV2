package com.autogear.menu;

import com.autogear.storage.ReadyNormalizer;
import com.autogear.storage.SetStorage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

record MenuSets(List<String> own, List<String> ready) {

    static final MenuSets EMPTY = new MenuSets(List.of(), List.of());

    private static final Executor WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "AutoGear set reader");
        thread.setDaemon(true);
        return thread;
    });

    static CompletableFuture<MenuSets> refresh(Boolean normalizeCurse) {
        return CompletableFuture.supplyAsync(() -> {
            if (normalizeCurse != null) ReadyNormalizer.normalizeAll(normalizeCurse);
            return new MenuSets(SetStorage.listSets(), SetStorage.listReadySets());
        }, WORKER);
    }

    static CompletableFuture<ItemStack[]> preview(String name, RegistryAccess registries) {
        return CompletableFuture.supplyAsync(() -> SetStorage.loadSet(name, registries), WORKER);
    }
}
