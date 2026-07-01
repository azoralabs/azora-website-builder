package dev.azora.website.builder.domain

import dev.azora.sdk.core.project.domain.AzoraProjectModel
import dev.azora.sdk.compiler.scene.domain.NavLink
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Bridges the website `navigation`/`config` scenes into the main `project.azora` file, stored under
 * dedicated keys in [dev.azora.sdk.core.project.domain.ProjectSettings.extras] so they are part of
 * the project model (not only the scene files).
 */
object WebsiteConfig {

    const val NAV_KEY = "website_nav"
    const val SETTINGS_KEY = "website_config"
    const val CONFIG_PATH_KEY = "website_config_path"
    const val NAV_PATH_KEY = "website_nav_path"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Returns a copy of [project] with [nav] written into its settings extras. */
    fun withNav(project: AzoraProjectModel, nav: List<NavLink>): AzoraProjectModel =
        withExtra(project, NAV_KEY, json.encodeToJsonElement(ListSerializer(NavLink.serializer()), nav))

    /** Returns a copy of [project] with site [settings] written into its settings extras. */
    fun withSettings(project: AzoraProjectModel, settings: Map<String, String>): AzoraProjectModel =
        withExtra(project, SETTINGS_KEY, json.encodeToJsonElement(MapSerializer(String.serializer(), String.serializer()), settings))

    fun withConfigPath(project: AzoraProjectModel, path: String): AzoraProjectModel =
        withString(project, CONFIG_PATH_KEY, path)

    fun withNavPath(project: AzoraProjectModel, path: String): AzoraProjectModel =
        withString(project, NAV_PATH_KEY, path)

    /** Path of the config .azn file, if set in extras. */
    fun configPath(project: AzoraProjectModel): String? = readString(project, CONFIG_PATH_KEY)

    /** Path of the navigation .azn file, if set in extras. */
    fun navPath(project: AzoraProjectModel): String? = readString(project, NAV_PATH_KEY)

    private fun withString(project: AzoraProjectModel, key: String, value: String): AzoraProjectModel =
        withExtra(project, key, kotlinx.serialization.json.JsonPrimitive(value))

    private fun readString(project: AzoraProjectModel, key: String): String? =
        (project.settings.extras[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun withExtra(project: AzoraProjectModel, key: String, value: kotlinx.serialization.json.JsonElement): AzoraProjectModel {
        val extras = JsonObject(project.settings.extras.toMutableMap().apply { put(key, value) })
        return project.copy(settings = project.settings.copy(extras = extras))
    }
}
