package org.kyowa.familyscanner.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.io.File

class ScannerConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File = FabricLoader.getInstance().configDir.resolve("familyscanner.json").toFile()

    val keywords: MutableSet<String> = mutableSetOf()

    fun load() {
        if (!configFile.exists()) return
        try {
            val type = object : TypeToken<Set<String>>() {}.type
            val loaded: Set<String> = gson.fromJson(configFile.readText(), type) ?: emptySet()
            keywords.clear()
            keywords.addAll(loaded)
        } catch (e: Exception) {
            keywords.clear()
        }
    }

    fun save() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(gson.toJson(keywords))
    }
}
