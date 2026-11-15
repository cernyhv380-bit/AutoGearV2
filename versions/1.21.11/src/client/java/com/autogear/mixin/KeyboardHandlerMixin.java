package com.autogear.mixin;

import com.autogear.actions.SetHotkeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {

    // This call is reached when vanilla dispatches a gameplay key, after screen input.
    @Inject(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
    private void autogear$queueSetKey(long window, int action, KeyEvent event,
                                     CallbackInfo ci) {
        if (action == InputConstants.PRESS) SetHotkeys.queueKeyPress(event.key());
    }
}
