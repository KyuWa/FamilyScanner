package org.kyowa.familyscanner.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Item
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.text.Text
import org.kyowa.familyscanner.FamilyScanner

private val COLOR_CODE_REGEX = Regex("§[0-9a-fklmnorA-FKLMNOR]")

object ContainerScanner {
    val matchingSlots: MutableSet<Int> = mutableSetOf()
    var hasMatch: Boolean = false
    private var debugPrinted = false

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen is HandledScreen<*>) {
                debugPrinted = false
                val raw = screen.title.string
                val stripped = raw.replace(COLOR_CODE_REGEX, "")
                client.player?.sendMessage(
                    Text.literal("§8[Scanner Debug] raw='§f$raw§8' stripped='§f$stripped§8'"),
                    false
                )
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = client.currentScreen
            if (screen !is HandledScreen<*>) {
                matchingSlots.clear()
                hasMatch = false
                debugPrinted = false
                return@register
            }

            val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
            if (!title.contains("Loot Chest", ignoreCase = true)) {
                matchingSlots.clear()
                hasMatch = false
                debugPrinted = false
                return@register
            }

            val player = client.player ?: return@register
            val world = client.world ?: return@register
            val keywords = FamilyScanner.config.keywords
            val handler = screen.screenHandler
            val chestSlots = handler.slots.filter { it.inventory !== player.inventory }

            // Wait until items have loaded in before printing debug
            val hasItems = chestSlots.any { !it.stack.isEmpty }
            if (!debugPrinted && hasItems) {
                debugPrinted = true
                var count = 0
                for (slot in chestSlots) {
                    if (count >= 5) break
                    val stack = slot.stack
                    if (stack.isEmpty) continue

                    val name = stack.name.string.replace(COLOR_CODE_REGEX, "")

                    val tooltipLines = stack.getTooltip(
                        Item.TooltipContext.create(world),
                        player,
                        TooltipType.Default.BASIC
                    )
                    val tooltip = tooltipLines.drop(1)
                        .joinToString(" | ") { it.string.replace(COLOR_CODE_REGEX, "") }
                        .ifEmpty { "(empty tooltip)" }

                    val customNbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.nbt?.toString()
                        ?.take(80) ?: "(no custom data)"

                    player.sendMessage(Text.literal("§e[Slot ${slot.id}] §f$name"), false)
                    player.sendMessage(Text.literal("§8  tooltip: §7$tooltip"), false)
                    player.sendMessage(Text.literal("§8  nbt: §7$customNbt"), false)
                    count++
                }
            }

            val newMatching = mutableSetOf<Int>()
            if (keywords.isNotEmpty()) {
                for (slot in chestSlots) {
                    val stack = slot.stack
                    if (stack.isEmpty) continue
                    val tooltipLines = stack.getTooltip(
                        Item.TooltipContext.create(world),
                        player,
                        TooltipType.Default.BASIC
                    )
                    val tooltipText = tooltipLines.joinToString("\n") {
                        it.string.replace(COLOR_CODE_REGEX, "").lowercase()
                    }
                    if (keywords.any { keyword -> tooltipText.contains(keyword) }) {
                        newMatching.add(slot.id)
                    }
                }
            }

            matchingSlots.clear()
            matchingSlots.addAll(newMatching)
            hasMatch = newMatching.isNotEmpty()
        }
    }
}
