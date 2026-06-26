package dev.azora.website.builder.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Plugin-internal bridge for **selection sync** between the node editor ([WebSceneEditor]) and the
 * Website Preview panel ([WebsitePreviewPanel]). They are separate panels with no shared Compose
 * state, but live in the same plugin classloader, so a singleton carries the selection between them:
 *
 * - clicking a node in the editor publishes its component id here → the preview highlights it;
 * - clicking an element in the preview publishes its id here → the editor selects the node.
 *
 * Component ids are unique per scene, so a published id only resolves in the scene/page that actually
 * contains it; other open scenes ignore it.
 */
object WebSelectionBus {
    val selectedId = MutableStateFlow<String?>(null)
    fun select(id: String?) { selectedId.value = id }
}
