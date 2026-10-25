package com.autogear.actions;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Регистрация клавиш мода.
 *   F4     — загрузить активный сет
 *   F8     — сохранить текущий инвентарь как новый set_N
 *   G      — открыть меню AutoGear
 *   Delete — очистить инвентарь (только в креативе)
 */
public final class KeyBinds {

    private static final String CATEGORY = "key.categories.autogear";

    public static KeyMapping KEY_LOAD;
    public static KeyMapping KEY_SAVE;
    public static KeyMapping KEY_MENU;
    public static KeyMapping KEY_CLEAR;

    private KeyBinds() {}

    public static void register() {
        KEY_LOAD = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autogear.load", InputConstants.Type.KEYSYM, InputConstants.KEY_F4, CATEGORY));
        KEY_SAVE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autogear.save", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, CATEGORY));
        KEY_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autogear.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY));
        KEY_CLEAR = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autogear.clear", InputConstants.Type.KEYSYM, InputConstants.KEY_DELETE, CATEGORY));
    }
}
