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
                                ctx.source.sendFeedback(Text.literal("No custom keywords configured."))
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
                            ctx.source.sendFeedback(Text.literal("All custom keywords cleared."))
                            1
                        }
                )
                .then(
                    literal("blockclose")
                        .then(
                            literal("enable")
                                .executes { ctx ->
                                    FamilyScanner.config.blockCloseEnabled = true
                                    FamilyScanner.config.save()
                                    ctx.source.sendFeedback(Text.literal("Block-close enabled. Press Ctrl+Esc to bypass."))
                                    1
                                }
                        )
                        .then(
                            literal("disable")
                                .executes { ctx ->
                                    FamilyScanner.config.blockCloseEnabled = false
                                    FamilyScanner.config.save()
                                    ctx.source.sendFeedback(Text.literal("Block-close disabled."))
                                    1
                                }
                        )
                        .executes { ctx ->
                            val state = if (FamilyScanner.config.blockCloseEnabled) "§aenabled" else "§cdisabled"
                            ctx.source.sendFeedback(Text.literal("Block-close is currently $state§r. Use enable/disable to change."))
                            1
                        }
                )
        )
    }
}
