package org.kyowa.familyscanner.mixin

import net.minecraft.client.gui.screen.ingame.HandledScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(HandledScreen::class)
interface HandledScreenAccessor {
    @Accessor("x")
    fun getGuiX(): Int

    @Accessor("y")
    fun getGuiY(): Int
}
