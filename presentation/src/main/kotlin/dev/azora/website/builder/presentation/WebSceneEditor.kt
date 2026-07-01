package dev.azora.website.builder.presentation
import dev.azora.sdk.compiler.scene.domain.SceneComponentTree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.azora.sdk.compiler.scene.domain.CanvasPoint
import dev.azora.sdk.compiler.scene.domain.SceneComponent
import dev.azora.sdk.compiler.scene.domain.SceneDocument

/**
 * Visual editor for a page/component scene: the [ComponentTreeCanvas] on the left and the
 * [ComponentPropertiesPanel] on the right, sharing a local selection. Operates over the scene's node
 * pool ([nodes], rooted at [rootId]).
 */
@Composable
fun WebSceneEditor(
    nodes: List<SceneComponent>,
    rootId: String,
    positions: Map<String, CanvasPoint>,
    onNodesChange: (List<SceneComponent>) -> Unit,
    onPersistPosition: (String, CanvasPoint) -> Unit,
    modifier: Modifier = Modifier,
    components: List<SceneDocument> = emptyList(),
    excludeName: String? = null,
    instances: Map<String, String> = emptyMap(),
    onInstance: (nodeId: String, componentName: String) -> Unit = { _, _ -> },
    onOpenComponent: (componentName: String) -> Unit = {}
) {
    var selectedId by remember(rootId) { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> SceneComponentTree.byId(nodes, id) }

    // Double-click detection: the canvas only reports single selections, so two selects of the same
    // node within the window count as a double-click. Double-clicking a component-instance node opens
    // that component's implementation in its own editor tab (via onOpenComponent).
    var lastClickId by remember(rootId) { mutableStateOf<String?>(null) }
    var lastClickAt by remember(rootId) { mutableStateOf(0L) }

    // Selection sync with the Website Preview panel via the shared bus: adopt a selection published
    // elsewhere (e.g. clicking an element in the preview) only when it resolves to a node in *this*
    // scene, so other open scenes don't clobber each other.
    val busSelection by WebSelectionBus.selectedId.collectAsState()
    LaunchedEffect(busSelection, nodes) {
        val id = busSelection
        if (id != selectedId && (id == null || SceneComponentTree.byId(nodes, id) != null)) {
            selectedId = id
        }
    }

    Row(modifier.fillMaxSize()) {
        ComponentTreeCanvas(
            nodes = nodes,
            rootId = rootId,
            persistedPositions = positions,
            selectedId = selectedId,
            onSelect = { id ->
                val now = System.currentTimeMillis()
                if (id != null && id == lastClickId && now - lastClickAt < 350) {
                    instances[id]?.let { name -> onOpenComponent(name) }
                }
                lastClickId = id
                lastClickAt = now
                selectedId = id
                WebSelectionBus.select(id)
            },
            onNodesChange = onNodesChange,
            onPersistPosition = onPersistPosition,
            modifier = Modifier.weight(1f),
            components = components,
            excludeName = excludeName,
            instances = instances,
            onInstance = onInstance
        )
        ComponentPropertiesPanel(
            selected = selected,
            isRoot = selected?.id == rootId,
            onChange = { edited -> onNodesChange(SceneComponentTree.replaceNode(nodes, edited.id) { edited }) },
            onDelete = {
                selected?.let { onNodesChange(SceneComponentTree.removeNode(nodes, it.id)) }
                selectedId = null
            }
        )
    }
}
