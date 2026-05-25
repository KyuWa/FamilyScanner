package org.kyowa.familyscanner.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.text.Text
import org.kyowa.familyscanner.FamilyScanner

object ScannerCommand {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            literal("scanner")
                .then(
                    literal("add")
                        .then(
                            argument("keyword", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val keyword = StringArgumentType.getString(ctx, "keyword").lowercase()
                                    FamilyScanner.config.keywords.add(keyword)
                                    FamilyScanner.config.save()
                                    ctx.source.sendFeedback(Text.literal("Added keyword: $keyword"))
                                    1
                                }
                        )
                )
                .then(
                    literal("remove")
                        .then(
                            argument("keyword", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val keyword = StringArgumentType.getString(ctx, "keyword").lowercase()
                                    val removed = FamilyScanner.config.keywords.remove(keyword)
                                    FamilyScanner.config.save()
                                    if (removed) {
                                        ctx.source.sendFeedback(Text.literal("Removed keyword: $keyword"))
                                    } else {
                                        ctx.source.sendFeedback(Text.literal("Keyword not found: $keyword"))
                                    }
                                    1
                                }
                        )
                )
                .then(
                    literal("list")
                        .executes { ctx ->
                            val keywords = FamilyScanner.config.keywords
                            if (keywords.isEmpty()) {
                                ctx.source.sendFeedback(Text.literal("No keywords configured."))
                            } else {
                                ctx.source.sendFeedback(Text.literal("Keywords: ${keywords.joinToString(", ")}"))
                            }
                            1
                        }
                )
                .then(
                    literal("clear")
                        .executes { ctx ->
                            FamilyScanner.config.keywords.clear()
                            FamilyScanner.config.save()
                            ctx.source.sendFeedback(Text.literal("All keywords cleared."))
                            1
                        }
                )
        )
    }
}
