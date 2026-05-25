package org.kyowa.familyscanner.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.text.Text
import org.kyowa.familyscanner.FamilyScanner

private val COLOR_CODE_REGEX = Regex("§[0-9a-fklmnorA-FKLMNOR]")

object ContainerScanner {
    val matchingSlots: MutableSet<Int> = mutableSetOf()
    var hasMatch: Boolean = false
    private var debugPrinted = false
    private val annotationCache = java.util.WeakHashMap<ItemStack, String>()

    // Lazy-find the Wynntils ItemHandler instance and annotation method once
    private val wynntilsHandlerAndMethod: Pair<Any?, java.lang.reflect.Method>? by lazy {
        tryFindWynntilsAnnotationMethod()
    }

    private fun tryFindWynntilsAnnotationMethod(): Pair<Any?, java.lang.reflect.Method>? {
        // Try to load the ItemHandler class
        val handlerClass = try {
            Class.forName("com.wynntils.handlers.item.ItemHandler")
        } catch (_: ClassNotFoundException) { return null } catch (_: Exception) { return null }

        // Find method named like getItemStackAnnotation with 1 param
        val method = handlerClass.methods.firstOrNull { m ->
            m.parameterCount == 1 &&
            (m.name.lowercase().contains("annotation"))
        } ?: return null

        // If static, receiver is null
        if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
            return Pair(null, method)
        }

        // Instance method — find the handler via Handlers or Managers holder class
        val holder = listOf(
            "com.wynntils.core.components.Handlers",
            "com.wynntils.core.components.Managers",
            "com.wynntils.managers.Managers"
        ).firstNotNullOfOrNull { cn ->
            try {
                val c = Class.forName(cn)
                c.fields.firstOrNull { f ->
                    f.type.isAssignableFrom(handlerClass) || f.name == "Item" || f.name == "item"
                }?.also { it.isAccessible = true }?.get(null)
            } catch (_: Exception) { null }
        } ?: return null

        return Pair(holder, method)
    }

    private fun getAnnotationText(stack: ItemStack): String {
        annotationCache[stack]?.let { return it }

        val (handler, method) = wynntilsHandlerAndMethod ?: return ""
        val result = try {
            method.invoke(handler, stack)
        } catch (_: Exception) { return "" }

        val annotation = when (result) {
            is java.util.Optional<*> -> result.orElse(null)
            else -> result
        } ?: return ""

        val text = buildString {
            for (m in annotation.javaClass.methods) {
                if (m.parameterCount != 0) continue
                try {
                    val v = m.invoke(annotation) ?: continue
                    when (v) {
                        is String -> if (v.isNotBlank()) append(v.lowercase()).append(" ")
                        is Collection<*> -> for (item in v) {
                            when (item) {
                                is String -> append(item.lowercase()).append(" ")
                                null -> {}
                                else -> {
                                    for (getter in listOf("getName", "name", "getDisplayName")) {
                                        try {
                                            val n = item.javaClass.getMethod(getter).invoke(item)
                                            if (n is String && n.isNotBlank()) {
                                                append(n.lowercase()).append(" ")
                                                break
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                } catch (_: Exception) {}
            }
        }.trim()

        if (text.isNotEmpty()) annotationCache[stack] = text
        return text
    }

    private fun debugSlot(
        slot: net.minecraft.screen.slot.Slot,
        stack: ItemStack,
        context: Item.TooltipContext,
        player: net.minecraft.entity.player.PlayerEntity
    ) {
        val name = stack.name.string.replace(COLOR_CODE_REGEX, "")
        val tooltipLines = stack.getTooltip(context, player, TooltipType.Default.BASIC)
        val tooltipStr = tooltipLines.drop(1)
            .joinToString(" | ") { it.string.replace(COLOR_CODE_REGEX, "") }
            .ifEmpty { "(empty)" }

        val nbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.toString() ?: "(none)"
        val componentList = stack.components.types
            .joinToString(",") { it.toString().substringAfterLast('.') }

        player.sendMessage(Text.literal("§e[Slot ${slot.id}] §f$name"), false)
        player.sendMessage(Text.literal("§8  components: §7$componentList"), false)
        player.sendMessage(Text.literal("§8  getTooltip: §7$tooltipStr"), false)
        player.sendMessage(Text.literal("§8  customNbt: §7${nbt.take(200)}"), false)

        // Wynntils annotation debug
        val (handler, method) = wynntilsHandlerAndMethod ?: run {
            player.sendMessage(Text.literal("§c  [W] Wynntils ItemHandler not found"), false)
            return
        }
        try {
            val result = method.invoke(handler, stack)
            val annotation = when (result) {
                is java.util.Optional<*> -> result.orElse(null)
                else -> result
            }
            if (annotation == null) {
                player.sendMessage(Text.literal("§c  [W] No annotation on this item"), false)
                return
            }
            player.sendMessage(Text.literal("§a  [W] ${annotation.javaClass.simpleName}"), false)
            for (m in annotation.javaClass.methods) {
                if (m.parameterCount != 0) continue
                try {
                    val v = m.invoke(annotation) ?: continue
                    val display = when (v) {
                        is Collection<*> -> v.joinToString(", ") { item ->
                            if (item is String) item
                            else {
                                var itemName = item?.toString() ?: "null"
                                for (getter in listOf("getName", "getDisplayName")) {
                                    try {
                                        val n = item?.javaClass?.getMethod(getter)?.invoke(item)
                                        if (n is String) { itemName = n; break }
                                    } catch (_: Exception) {}
                                }
                                itemName
                            }
                        }.take(120)
                        else -> v.toString().take(120)
                    }
                    player.sendMessage(Text.literal("§7    ${m.name}: §f$display"), false)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            player.sendMessage(Text.literal("§c  [W] Error: ${e.message?.take(80)}"), false)
        }
    }

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen is HandledScreen<*>) {
                debugPrinted = false
                annotationCache.clear()
                val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
                client.player?.sendMessage(
                    Text.literal("§8[Scanner] container: §f$title"),
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
            val tooltipCtx = Item.TooltipContext.create(world)

            val hasItems = chestSlots.any { !it.stack.isEmpty }
            if (!debugPrinted && hasItems) {
                debugPrinted = true
                var count = 0
                for (slot in chestSlots) {
                    if (count >= 3) break
                    val stack = slot.stack
                    if (stack.isEmpty) continue
                    count++
                    debugSlot(slot, stack, tooltipCtx, player)
                }
            }

            val newMatching = mutableSetOf<Int>()
            if (keywords.isNotEmpty()) {
                for (slot in chestSlots) {
                    val stack = slot.stack
                    if (stack.isEmpty) continue

                    val vanillaTooltip = stack.getTooltip(tooltipCtx, player, TooltipType.Default.BASIC)
                        .joinToString("\n") { it.string.replace(COLOR_CODE_REGEX, "").lowercase() }
                    val wynntilsText = getAnnotationText(stack)
                    val combined = "$vanillaTooltip $wynntilsText"

                    if (keywords.any { kw -> combined.contains(kw) }) {
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
