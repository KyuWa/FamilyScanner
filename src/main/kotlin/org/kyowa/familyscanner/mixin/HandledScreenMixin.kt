package org.kyowa.familyscanner.mixin

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.sound.SimpleSoundInstance
import net.minecraft.screen.ScreenHandler
import net.minecraft.sound.SoundEvents
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.kyowa.familyscanner.FamilyScanner
import org.kyowa.familyscanner.features.ContainerScanner

@Mixin(HandledScreen::class)
abstract class HandledScreenMixin<T : ScreenHandler> {

    // Intercept keyPressed so we have reliable Ctrl detection via the modifiers bitmask.
    // Injecting into close() loses the modifier state; keyPressed fires before close() is called.
    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    private fun onKeyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return
        if (!ContainerScanner.hasMatch) return
        if (!FamilyScanner.config.blockCloseEnabled) return
        val ctrlHeld = (modifiers and GLFW.GLFW_MOD_CONTROL) != 0
        if (!ctrlHeld) {
            MinecraftClient.getInstance().soundManager.play(
                SimpleSoundInstance.forUi(SoundEvents.BLOCK_ANVIL_BREAK, 1.0f)
            )
            ci.setReturnValue(true)
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
