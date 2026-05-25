package org.kyowa.familyscanner.mixin

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.screen.ScreenHandler
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.random.Random
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

    // In 1.21.11 keyPressed takes a single key-event object instead of (int,int,int).
    // We capture no parameters and read key state from GLFW instead.
    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    private fun onKeyPressed(ci: CallbackInfoReturnable<Boolean>) {
        val client = MinecraftClient.getInstance()
        val window = client.window.handle
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) != GLFW.GLFW_PRESS) return
        if (!ContainerScanner.hasMatch) return
        if (!FamilyScanner.config.blockCloseEnabled) return
        val ctrlHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                       GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        if (!ctrlHeld) {
            client.soundManager.play(
                PositionedSoundInstance(
                    SoundEvents.BLOCK_ANVIL_BREAK,
                    SoundCategory.MASTER,
                    1.0f, 1.0f,
                    Random.create(),
                    0.0, 0.0, 0.0
                )
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
