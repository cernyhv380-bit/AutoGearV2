package com.autogear.actions;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Регистрация клавиш мода.
 *   F4     — загрузить активный сет
 *   F8     — сохранить текущий инвентарь как новый set_N
 *   G      — открыть меню AutoGear
 *   Delete — очистить инвентарь (только в креативе)
 */
public final class KeyBinds {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autogear", "main"));

    public static KeyMapping KEY_LOAD;
    public static KeyMapping KEY_SAVE;
    public static KeyMapping KEY_MENU;
    public static KeyMapping KEY_CLEAR;

    private KeyBinds() {}

    public static void register() {
        KEY_LOAD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autogear.load",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F4,
                CATEGORY
        ));

        KEY_SAVE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autogear.save",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F8,
                CATEGORY
        ));

        KEY_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autogear.menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_G,
                CATEGORY
        ));

        KEY_CLEAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autogear.clear",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_DELETE,
                CATEGORY
        ));
    }
}
