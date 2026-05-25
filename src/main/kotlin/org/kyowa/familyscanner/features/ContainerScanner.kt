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

    private val wynntilsHandlerAndMethod: Pair<Any?, java.lang.reflect.Method>? by lazy {
        tryFindWynntilsAnnotationMethod()
    }

    private fun tryFindWynntilsAnnotationMethod(): Pair<Any?, java.lang.reflect.Method>? {
        val handlerClass = try {
            Class.forName("com.wynntils.handlers.item.ItemHandler")
        } catch (_: Exception) { return null }

        val method = handlerClass.methods.firstOrNull { m ->
            m.parameterCount == 1 && m.name.lowercase().contains("annotation")
        } ?: return null

        if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
            return Pair(null, method)
        }

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

    private fun resolveAnnotation(stack: ItemStack): Any? {
        val (handler, method) = wynntilsHandlerAndMethod ?: return null
        val result = try { method.invoke(handler, stack) } catch (_: Exception) { return null }
        return when (result) {
            is java.util.Optional<*> -> result.orElse(null)
            else -> result
        }
    }

    // Collect searchable text from an arbitrary object (one level deep).
    private fun collectText(obj: Any): String = buildString {
        for (m in obj.javaClass.methods) {
            if (m.parameterCount != 0 || m.declaringClass == Any::class.java) continue
            val v = try { m.invoke(obj) } catch (_: Exception) { continue } ?: continue
            appendValue(v)
        }
    }

    private fun StringBuilder.appendValue(v: Any) {
        when (v) {
            is String -> if (v.isNotBlank()) append(v.lowercase()).append(' ')
            is Collection<*> -> for (item in v) {
                if (item == null) continue
                if (item is String) { append(item.lowercase()).append(' '); continue }
                // try getName first, fall back to toString if no memory address
                var named = false
                for (getter in listOf("getName", "getDisplayName")) {
                    try {
                        val n = item.javaClass.getMethod(getter).invoke(item)
                        if (n is String && n.isNotBlank()) { append(n.lowercase()).append(' '); named = true; break }
                    } catch (_: Exception) {}
                }
                if (!named) {
                    val s = item.toString()
                    if (!s.contains('@')) append(s.lowercase()).append(' ')
                }
            }
            else -> {
                val s = v.toString()
                // skip memory-address strings and huge blobs
                if (!s.contains('@') && s.length < 200) append(s.lowercase()).append(' ')
            }
        }
    }

    private fun getAnnotationText(stack: ItemStack): String {
        annotationCache[stack]?.let { return it }
        val annotation = resolveAnnotation(stack) ?: return ""

        val text = buildString {
            // annotation.toString() already contains type/tier/range for GearBoxItem
            val top = annotation.toString()
            if (!top.contains('@')) append(top.lowercase()).append(' ')

            // also mine the annotation's own getters for strings/collections
            for (m in annotation.javaClass.methods) {
                if (m.parameterCount != 0 || m.declaringClass == Any::class.java) continue
                val v = try { m.invoke(annotation) } catch (_: Exception) { continue } ?: continue
                appendValue(v)
                // one level deep: if the value is a non-trivial object, explore it too
                if (v !is String && v !is Collection<*> && v !is Enum<*> && v !is Number && v !is Boolean) {
                    append(collectText(v))
                }
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
        val tooltipStr = stack.getTooltip(context, player, TooltipType.Default.BASIC)
            .drop(1).joinToString(" | ") { it.string.replace(COLOR_CODE_REGEX, "") }.ifEmpty { "(empty)" }

        player.sendMessage(Text.literal("§e[Slot ${slot.id}] §f$name"), false)
        player.sendMessage(Text.literal("§8  getTooltip: §7$tooltipStr"), false)

        val annotation = resolveAnnotation(stack)
        if (annotation == null) {
            val (_, _) = wynntilsHandlerAndMethod ?: run {
                player.sendMessage(Text.literal("§c  [W] Wynntils ItemHandler not found"), false)
                return
            }
            player.sendMessage(Text.literal("§c  [W] No annotation on this item"), false)
            return
        }

        player.sendMessage(Text.literal("§a  [W] ${annotation.javaClass.simpleName}: §7${annotation.toString().take(100)}"), false)

        // Show WynnItemData contents
        try {
            val dataMethod = annotation.javaClass.getMethod("getData")
            val data = dataMethod.invoke(annotation)
            if (data != null) {
                player.sendMessage(Text.literal("§b  [WynnItemData] ${data.javaClass.simpleName}"), false)
                for (m in data.javaClass.methods) {
                    if (m.parameterCount != 0 || m.declaringClass == Any::class.java) continue
                    val v = try { m.invoke(data) } catch (_: Exception) { continue } ?: continue
                    val display = when (v) {
                        is Collection<*> -> v.take(5).joinToString(", ") { item ->
                            if (item == null) "null"
                            else {
                                var s = item.toString()
                                for (g in listOf("getName", "getDisplayName")) {
                                    try { val n = item.javaClass.getMethod(g).invoke(item); if (n is String) { s = n; break } } catch (_: Exception) {}
                                }
                                s
                            }
                        }.take(120) + if (v.size > 5) " (+${v.size - 5})" else ""
                        else -> v.toString().take(120)
                    }
                    player.sendMessage(Text.literal("§7    ${m.name}: §f$display"), false)
                }
            }
        } catch (_: Exception) {}
    }

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen is HandledScreen<*>) {
                debugPrinted = false
                annotationCache.clear()
                val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
                client.player?.sendMessage(Text.literal("§8[Scanner] container: §f$title"), false)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = client.currentScreen
            if (screen !is HandledScreen<*>) {
                matchingSlots.clear(); hasMatch = false; debugPrinted = false; return@register
            }

            val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
            if (!title.contains("Loot Chest", ignoreCase = true)) {
                matchingSlots.clear(); hasMatch = false; debugPrinted = false; return@register
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
                        .joinToString(" ") { it.string.replace(COLOR_CODE_REGEX, "").lowercase() }
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
