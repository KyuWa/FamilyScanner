package org.kyowa.familyscanner.mixin

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.screen.ScreenHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.kyowa.familyscanner.features.ContainerScanner

@Mixin(HandledScreen::class)
abstract class HandledScreenMixin<T : ScreenHandler> {

    @Inject(method = ["close"], at = [At("HEAD")], cancellable = true)
    private fun onClose(ci: CallbackInfo) {
        if (ContainerScanner.hasMatch && !Screen.hasControlDown()) {
            ci.cancel()
        }
    }

    @Inject(method = ["render"], at = [At("TAIL")])
    private fun onRender(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float, ci: CallbackInfo) {
        if (ContainerScanner.matchingSlots.isEmpty()) return

        val screen = this as HandledScreen<*>
        val accessor = this as HandledScreenAccessor
        val guiX = accessor.getGuiX()
        val guiY = accessor.getGuiY()

        for (slot in screen.screenHandler.slots) {
            if (slot.id in ContainerScanner.matchingSlots) {
                val slotX = guiX + slot.x
                val slotY = guiY + slot.y
                context.fill(slotX, slotY, slotX + 16, slotY + 16, 0x8000FF00.toInt())
            }
        }
    }
}
