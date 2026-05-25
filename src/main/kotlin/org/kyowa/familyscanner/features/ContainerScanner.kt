package org.kyowa.familyscanner.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.Item
import net.minecraft.item.tooltip.TooltipType
import org.kyowa.familyscanner.FamilyScanner

object ContainerScanner {
    val matchingSlots: MutableSet<Int> = mutableSetOf()
    var hasMatch: Boolean = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = client.currentScreen
            if (screen !is HandledScreen<*>) {
                matchingSlots.clear()
                hasMatch = false
                return@register
            }

            if (!screen.title.string.equals("Loot Chest", ignoreCase = true)) {
                matchingSlots.clear()
                hasMatch = false
                return@register
            }

            val player = client.player ?: return@register
            val world = client.world ?: return@register
            val keywords = FamilyScanner.config.keywords

            if (keywords.isEmpty()) {
                matchingSlots.clear()
                hasMatch = false
                return@register
            }

            val handler = screen.screenHandler
            val newMatching = mutableSetOf<Int>()

            for (slot in handler.slots) {
                val stack = slot.stack
                if (stack.isEmpty) continue

                val tooltipLines = stack.getTooltip(
                    Item.TooltipContext.create(world),
                    player,
                    TooltipType.Default.BASIC
                )

                val tooltipText = tooltipLines.joinToString("\n") { it.string.lowercase() }

                if (keywords.any { keyword -> tooltipText.contains(keyword) }) {
                    newMatching.add(slot.id)
                }
            }

            matchingSlots.clear()
            matchingSlots.addAll(newMatching)
            hasMatch = newMatching.isNotEmpty()
        }
    }
}
