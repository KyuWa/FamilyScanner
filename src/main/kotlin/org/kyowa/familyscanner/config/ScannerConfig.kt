package org.kyowa.familyscanner.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class ScannerConfigData(
    val keywords: MutableSet<String> = mutableSetOf(),
    var blockCloseEnabled: Boolean = true
)

class ScannerConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File =
        FabricLoader.getInstance().configDir.resolve("familyscanner.json").toFile()

    private var data = ScannerConfigData()

    val keywords: MutableSet<String> get() = data.keywords
    var blockCloseEnabled: Boolean
        get() = data.blockCloseEnabled
        set(value) { data.blockCloseEnabled = value }

    fun load() {
        if (!configFile.exists()) return
        runCatching {
            val text = configFile.readText()
            val json = JsonParser.parseString(text)
            when {
                json.isJsonObject -> {
                    // New format: { "keywords": [...], "blockCloseEnabled": true }
                    data = gson.fromJson(text, ScannerConfigData::class.java) ?: ScannerConfigData()
                }
                json.isJsonArray -> {
                    // Old format: ["keyword1", "keyword2"]
                    val kws = (json as JsonArray).mapNotNull {
                        if (it.isJsonPrimitive) it.asString else null
                    }.toMutableSet()
                    data = ScannerConfigData(kws)
                }
            }
        }
    }

    fun save() {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(data))
        }
    }
}
