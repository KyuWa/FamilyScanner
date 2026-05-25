package org.kyowa.familyscanner.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.text.Text
import org.kyowa.familyscanner.FamilyScanner

private val COLOR_CODE_REGEX = Regex("§[0-9a-fklmnorA-FKLMNOR]")

object ContainerScanner {
    val matchingSlots: MutableSet<Int> = mutableSetOf()
    var hasMatch: Boolean = false
    private var lastDebugTitle: String? = null

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen is HandledScreen<*>) {
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
                lastDebugTitle = null
                return@register
            }

            val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
            if (!title.contains("Loot Chest", ignoreCase = true)) {
                matchingSlots.clear()
                hasMatch = false
                lastDebugTitle = null
                return@register
            }

            val player = client.player ?: return@register
            val keywords = FamilyScanner.config.keywords
            val handler = screen.screenHandler

            // Only chest slots, not player inventory
            val chestSlots = handler.slots.filter { it.inventory !== player.inventory }

            // Debug: print first 5 non-empty chest slots once per chest open
            if (lastDebugTitle != title) {
                lastDebugTitle = title
                var debugCount = 0
                for (slot in chestSlots) {
                    if (debugCount >= 5) break
                    val stack = slot.stack
                    if (stack.isEmpty) continue
                    val name = stack.name.string.replace(COLOR_CODE_REGEX, "")
                    val lore = stack.get(DataComponentTypes.LORE)?.lines
                        ?.joinToString(" §8|§f ") { it.string.replace(COLOR_CODE_REGEX, "") }
                        ?: "§7(no lore)"
                    player.sendMessage(
                        Text.literal("§8[Slot ${slot.id}] §f$name §8>> §f$lore"),
                        false
                    )
                    debugCount++
                }
            }

            val newMatching = mutableSetOf<Int>()

            if (keywords.isNotEmpty()) {
                for (slot in chestSlots) {
                    val stack = slot.stack
                    if (stack.isEmpty) continue

                    val name = stack.name.string.replace(COLOR_CODE_REGEX, "").lowercase()
                    val loreText = stack.get(DataComponentTypes.LORE)?.lines
                        ?.joinToString("\n") { it.string.replace(COLOR_CODE_REGEX, "").lowercase() }
                        ?: ""
                    val fullText = "$name\n$loreText"

                    if (keywords.any { keyword -> fullText.contains(keyword) }) {
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
