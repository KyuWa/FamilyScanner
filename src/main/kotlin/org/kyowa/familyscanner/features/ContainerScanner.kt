package org.kyowa.familyscanner.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import org.kyowa.familyscanner.FamilyScanner

private val COLOR_CODE_REGEX = Regex("§[0-9a-fklmnorA-FKLMNOR]")

object ContainerScanner {
    val matchingSlots: MutableSet<Int> = mutableSetOf()
    var hasMatch: Boolean = false
    private val annotationCache = java.util.WeakHashMap<ItemStack, String>()

    private val wynntilsHandlerAndMethod: Pair<Any?, java.lang.reflect.Method>? by lazy {
        tryFindWynntilsAnnotationMethod()
    }

    // ── GearBox possibilities ────────────────────────────────────────────────

    private data class GearBoxProps(
        val gearType: String,
        val gearTier: String,
        val minLevel: Int,
        val maxLevel: Int
    )

    // Populated from a background thread; ConcurrentHashMap is safe to read from the tick thread.
    private val gearPossibilitiesCache =
        java.util.concurrent.ConcurrentHashMap<GearBoxProps, List<String>>()
    private val computingProps: MutableSet<GearBoxProps> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val wynntilsGearModelPair: Pair<Any?, java.lang.reflect.Method>? by lazy {
        tryFindGearModelAllItemsMethod()
    }

    private fun tryFindGearModelAllItemsMethod(): Pair<Any?, java.lang.reflect.Method>? {
        val modelsClass = try {
            Class.forName("com.wynntils.core.components.Models")
        } catch (_: Exception) { return null }

        val modelInstance: Any = listOf("Gear", "GearItem", "Item", "WynnItem").firstNotNullOfOrNull { name ->
            try { modelsClass.getField(name).get(null) } catch (_: Exception) { null }
        } ?: modelsClass.fields.firstNotNullOfOrNull { field ->
            try {
                val inst = field.get(null) ?: return@firstNotNullOfOrNull null
                val hasGearListMethod = inst.javaClass.methods.any { m ->
                    m.parameterCount == 0 &&
                    m.name.lowercase().let { n ->
                        n.contains("gear") && (n.contains("all") || n.contains("info") || n.contains("list"))
                    }
                }
                if (hasGearListMethod) inst else null
            } catch (_: Exception) { null }
        } ?: return null

        val method = modelInstance.javaClass.methods.firstOrNull { m ->
            m.parameterCount == 0 &&
            (java.lang.Iterable::class.java.isAssignableFrom(m.returnType) ||
             java.util.Collection::class.java.isAssignableFrom(m.returnType) ||
             java.util.stream.Stream::class.java.isAssignableFrom(m.returnType) ||
             java.util.Map::class.java.isAssignableFrom(m.returnType)) &&
            m.name.lowercase().let { n ->
                (n.contains("all") || n.contains("gear") || n.contains("info") || n.contains("item")) &&
                !n.contains("set") && !n.contains("add")
            }
        } ?: return null

        return Pair(modelInstance, method)
    }

    private fun invokeGetter(obj: Any, name: String): Any? =
        try { obj.javaClass.getMethod(name).invoke(obj) } catch (_: Exception) { null }

    private fun asString(v: Any?): String? {
        v ?: return null
        if (v is String) return v.ifEmpty { null }
        val s = v.toString()
        return if (!s.contains('@')) s.ifEmpty { null } else null
    }

    private fun asInt(v: Any?): Int? = when (v) {
        is Int -> v
        is Number -> v.toInt()
        is java.util.Optional<*> -> (v.orElse(null) as? Number)?.toInt()
        is java.util.OptionalInt -> if (v.isPresent) v.asInt else null
        is java.util.OptionalLong -> if (v.isPresent) v.asLong.toInt() else null
        else -> null
    }

    private fun getGearTierStr(item: Any): String? {
        asString(invokeGetter(item, "tier"))?.let { return it }
        asString(invokeGetter(item, "getGearTier"))?.let { return it }
        asString(invokeGetter(item, "getTier"))?.let { return it }
        val meta = invokeGetter(item, "metaInfo") ?: invokeGetter(item, "getMetaInfo") ?: return null
        asString(invokeGetter(meta, "tier"))?.let { return it }
        asString(invokeGetter(meta, "getTier"))?.let { return it }
        return null
    }

    private fun getGearTypeStr(item: Any): String? {
        asString(invokeGetter(item, "type"))?.let { return it }
        asString(invokeGetter(item, "getGearType"))?.let { return it }
        asString(invokeGetter(item, "getType"))?.let { return it }
        val meta = invokeGetter(item, "metaInfo") ?: invokeGetter(item, "getMetaInfo") ?: return null
        asString(invokeGetter(meta, "type"))?.let { return it }
        asString(invokeGetter(meta, "getType"))?.let { return it }
        return null
    }

    private fun getItemLevelInt(item: Any): Int? {
        asInt(invokeGetter(item, "level"))?.let { return it }
        asInt(invokeGetter(item, "getLevel"))?.let { return it }
        val reqs = invokeGetter(item, "requirements") ?: invokeGetter(item, "getRequirements") ?: return null
        asInt(invokeGetter(reqs, "level"))?.let { return it }
        asInt(invokeGetter(reqs, "getLevel"))?.let { return it }
        return null
    }

    private fun getItemNameStr(item: Any): String? {
        asString(invokeGetter(item, "name"))?.let { return it }
        asString(invokeGetter(item, "getName"))?.let { return it }
        asString(invokeGetter(item, "getDisplayName"))?.let { return it }
        return null
    }

    private fun rawToList(raw: Any?): List<Any> = when (raw) {
        is java.util.stream.Stream<*> -> raw.toList()
        is Map<*, *> -> raw.values.toList()
        is Iterable<*> -> raw.toList()
        else -> emptyList()
    }.filterNotNull()

    private fun extractGearBoxProps(annotation: Any): GearBoxProps? {
        return try {
            val cls = annotation.javaClass
            val gearType = cls.getMethod("getGearType").invoke(annotation)?.toString()?.uppercase()
                ?: return null
            val gearTier = cls.getMethod("getGearTier").invoke(annotation)?.toString()
                ?: return null
            val maxLevel = cls.getMethod("getLevel").invoke(annotation) as? Int ?: return null
            val rangeStr = try {
                cls.getMethod("getLevelRange").invoke(annotation)?.toString() ?: ""
            } catch (_: Exception) { "" }
            val minLevel = Regex("""<(\d+)-(\d+)>""").find(rangeStr)
                ?.groupValues?.get(1)?.toIntOrNull() ?: maxLevel
            GearBoxProps(gearType, gearTier, minLevel, maxLevel)
        } catch (_: Exception) { null }
    }

    private fun matchGearInfo(item: Any, props: GearBoxProps): String? {
        val name = getItemNameStr(item) ?: return null
        val tier = getGearTierStr(item) ?: return null
        val type = getGearTypeStr(item) ?: return null
        val level = getItemLevelInt(item) ?: return null
        return if (type.uppercase() == props.gearType &&
            tier.uppercase() == props.gearTier.uppercase() &&
            level >= props.minLevel && level <= props.maxLevel
        ) name else null
    }

    private fun computePossibleGearNames(props: GearBoxProps): List<String> {
        val (modelInstance, getAllMethod) = wynntilsGearModelPair ?: return emptyList()
        val raw = try { getAllMethod.invoke(modelInstance) } catch (_: Exception) { return emptyList() }
        return rawToList(raw).mapNotNull { matchGearInfo(it, props) }.sorted()
    }

    // Returns cached list immediately; starts a background thread on first miss.
    private fun getPossibleGearNames(props: GearBoxProps): List<String> {
        gearPossibilitiesCache[props]?.let { return it }
        if (computingProps.add(props)) {
            Thread {
                val result = computePossibleGearNames(props)
                gearPossibilitiesCache[props] = result
                computingProps.remove(props)
            }.also { it.isDaemon = true }.start()
        }
        return emptyList()
    }

    // ── Wynntils annotation helpers ──────────────────────────────────────────

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
                var named = false
                for (getter in listOf("getName", "getDisplayName")) {
                    try {
                        val n = item.javaClass.getMethod(getter).invoke(item)
                        if (n is String && n.isNotBlank()) {
                            append(n.lowercase()).append(' ')
                            named = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!named) {
                    val s = item.toString()
                    if (!s.contains('@')) append(s.lowercase()).append(' ')
                }
            }
            else -> {
                val s = v.toString()
                if (!s.contains('@') && s.length < 200) append(s.lowercase()).append(' ')
            }
        }
    }

    private fun getAnnotationText(stack: ItemStack): String {
        annotationCache[stack]?.let { return it }
        val annotation = resolveAnnotation(stack) ?: return ""

        val text = buildString {
            val top = annotation.toString()
            if (!top.contains('@')) append(top.lowercase()).append(' ')

            for (m in annotation.javaClass.methods) {
                if (m.parameterCount != 0 || m.declaringClass == Any::class.java) continue
                val v = try { m.invoke(annotation) } catch (_: Exception) { continue } ?: continue
                appendValue(v)
                if (v !is String && v !is Collection<*> && v !is Enum<*> && v !is Number && v !is Boolean) {
                    append(collectText(v))
                }
            }

            if (annotation.javaClass.simpleName == "GearBoxItem") {
                val props = extractGearBoxProps(annotation)
                if (props != null) {
                    for (name in getPossibleGearNames(props)) {
                        append(name.lowercase()).append(' ')
                    }
                }
            }
        }.trim()

        if (text.isNotEmpty()) annotationCache[stack] = text
        return text
    }

    // ── Main registration ────────────────────────────────────────────────────

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is HandledScreen<*>) {
                annotationCache.clear()
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = client.currentScreen
            if (screen !is HandledScreen<*>) {
                matchingSlots.clear(); hasMatch = false; return@register
            }

            val title = screen.title.string.replace(COLOR_CODE_REGEX, "")
            if (!title.contains("Loot Chest", ignoreCase = true)) {
                matchingSlots.clear(); hasMatch = false; return@register
            }

            val player = client.player ?: return@register
            val world = client.world ?: return@register
            val keywords = FamilyScanner.config.keywords
            if (keywords.isEmpty()) {
                matchingSlots.clear(); hasMatch = false; return@register
            }

            val handler = screen.screenHandler
            val chestSlots = handler.slots.filter { it.inventory !== player.inventory }
            val tooltipCtx = Item.TooltipContext.create(world)

            val newMatching = mutableSetOf<Int>()
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

            matchingSlots.clear()
            matchingSlots.addAll(newMatching)
            hasMatch = newMatching.isNotEmpty()
        }
    }
}
