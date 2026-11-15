package com.autogear.actions;

import com.autogear.storage.SetStorage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class SetHotkeys {

    private static String pendingSet;

    private SetHotkeys() {}

    public static String label(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    public static boolean isReserved(int key) {
        if (key == InputConstants.KEY_F2 || key == InputConstants.KEY_F3 || key == InputConstants.KEY_F11) return true;
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getName();
        return matches(KeyBinds.KEY_LOAD, name) || matches(KeyBinds.KEY_SAVE, name)
                || matches(KeyBinds.KEY_CLEAR, name) || matches(KeyBinds.KEY_MENU, name);
    }

    private static boolean matches(KeyMapping mapping, String key) {
        return mapping != null && mapping.saveString().equals(key);
    }

    public static void queueKeyPress(int key) {
        if (pendingSet != null || isReserved(key)) return;
        for (var binding : SetStorage.getSetKeyBindings().entrySet()) {
            if (binding.getValue() == key) {
                pendingSet = binding.getKey();
                return;
            }
        }
    }

    static void tick(Minecraft client) {
        String name = pendingSet;
        pendingSet = null;
        if (name != null && client.player != null && client.level != null
                && client.screen == null && client.isWindowActive()) {
            GearActions.loadFromMenu(name);
        }
    }
}
