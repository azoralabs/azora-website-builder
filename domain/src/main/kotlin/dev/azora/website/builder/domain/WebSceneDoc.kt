package dev.azora.website.builder.domain

import dev.azora.website.builder.domain.WebColumn
import dev.azora.website.builder.domain.WebComponent
import kotlinx.serialization.Serializable

/** The `.azscene` `type` discriminator values owned by the website builder. */
object WebSceneType {
    const val PAGE = "azora-website-page"
    const val COMPONENT = "azora-website-component"
    const val NAVIGATION = "azora-website-navigation"
    const val CONFIG = "azora-website-config"

    val all = setOf(PAGE, COMPONENT, NAVIGATION, CONFIG)
}

/** A position on the node-editor canvas, in canvas-local (pre-pan) coordinates. */
@Serializable
data class CanvasPoint(val x: Float = 0f, val y: Float = 0f)

/** One site-navigation entry. */
@Serializable
data class NavLink(val label: String, val route: String)

/**
 * Contents of a website builder `.azscene` file. The top-level [type] is the generic discriminator
 * Studio reads to route the file to this plugin; the rest is website-specific.
 *
 * - `PAGE` / `COMPONENT`: a visual [root] tree (+ canvas [positions] and component [instances],
 *   keyed anchor-node-id → component name). Pages also carry a [route].
 * - `NAVIGATION`: the site [nav], edited by linking the nav node to pages. On save the plugin also
 *   writes [nav] into `project.azora` (see WebsiteConfig).
 * - `CONFIG`: site-wide [settings] (e.g. `title`, `themeColor`), also mirrored into `project.azora`.
 */
@Serializable
data class WebSceneDoc(
    val type: String = WebSceneType.PAGE,
    val name: String = "",
    val route: String = "/",
    val root: WebComponent = WebColumn(),
    val positions: Map<String, CanvasPoint> = emptyMap(),
    val instances: Map<String, String> = emptyMap(),
    val nav: List<NavLink> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)
