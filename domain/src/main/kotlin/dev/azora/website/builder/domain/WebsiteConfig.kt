package dev.azora.website.builder.domain

import dev.azora.sdk.core.project.domain.AzoraProjectModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Bridges the website `navigation`/`config` scenes into the main `project.azora` file, stored under
 * dedicated keys in [dev.azora.sdk.core.project.domain.ProjectSettings.extras] so they are part of
 * the project model (not only the scene files).
 */
object WebsiteConfig {

    const val NAV_KEY = "website_nav"
    const val SETTINGS_KEY = "website_config"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Returns a copy of [project] with [nav] written into its settings extras. */
    fun withNav(project: AzoraProjectModel, nav: List<NavLink>): AzoraProjectModel =
        withExtra(project, NAV_KEY, json.encodeToJsonElement(ListSerializer(NavLink.serializer()), nav))

    /** Returns a copy of [project] with site [settings] written into its settings extras. */
    fun withSettings(project: AzoraProjectModel, settings: Map<String, String>): AzoraProjectModel =
        withExtra(project, SETTINGS_KEY, json.encodeToJsonElement(MapSerializer(String.serializer(), String.serializer()), settings))

    private fun withExtra(project: AzoraProjectModel, key: String, value: kotlinx.serialization.json.JsonElement): AzoraProjectModel {
        val extras = JsonObject(project.settings.extras.toMutableMap().apply { put(key, value) })
        return project.copy(settings = project.settings.copy(extras = extras))
    }
}
