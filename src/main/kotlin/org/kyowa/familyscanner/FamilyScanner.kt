package org.kyowa.familyscanner

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.kyowa.familyscanner.commands.ScannerCommand
import org.kyowa.familyscanner.config.ScannerConfig
import org.kyowa.familyscanner.features.ContainerScanner

@Environment(EnvType.CLIENT)
object FamilyScanner : ClientModInitializer {
    val config = ScannerConfig()

    override fun onInitializeClient() {
        config.load()
        ContainerScanner.register()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            ScannerCommand.register(dispatcher)
        }
    }
}
