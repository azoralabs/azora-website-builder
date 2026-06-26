package dev.azora.website.builder.presentation

import dev.azora.website.builder.domain.WebSceneDoc
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Plugin-internal bridge for **live (in-memory) scene sync** from the node editor
 * ([AzsceneEditorScreen] → [WebSceneEditor]) to the Website Preview panel
 * ([WebsitePreviewPanel]). Like [WebSelectionBus], it is a singleton in the shared plugin
 * classloader because the two panels have no common Compose state holder.
 *
 * The editor publishes the latest [WebSceneDoc] on every change (before the debounced disk write);
 * the preview overlays these live docs over its disk-loaded pages/components (keyed by [name]) so an
 * edit appears in the preview the same frame instead of waiting on the 2 s disk poll + autosave.
 * The poll stays as a backstop for structural/external changes (new files, scenes edited elsewhere).
 *
 * Keys are the scene's effective name (file base name) — the same identity `WebSceneFiles.read`
 * yields — so a live doc lines up with the disk doc it overrides. An editor retracts its entry on
 * dispose so stale live data can't shadow the disk after it closes.
 */
object WebSceneBus {
    val liveScenes = MutableStateFlow<Map<String, WebSceneDoc>>(emptyMap())

    fun publish(doc: WebSceneDoc) {
        val key = doc.name.trim()
        if (key.isBlank()) return
        liveScenes.value = liveScenes.value + (key to doc)
    }

    fun retract(name: String) {
        val key = name.trim()
        if (key.isBlank()) return
        liveScenes.value = liveScenes.value - key
    }
}
