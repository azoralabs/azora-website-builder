package dev.azora.website.builder.presentation
import dev.azora.website.builder.domain.WebComponentTree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.azora.website.builder.domain.WebComponent
import dev.azora.website.builder.domain.CanvasPoint
import dev.azora.website.builder.domain.WebSceneDoc

/**
 * Visual editor for a page/component scene: the [ComponentTreeCanvas] on the left and the
 * [ComponentPropertiesPanel] on the right, sharing a local selection.
 */
@Composable
fun WebSceneEditor(
    root: WebComponent,
    positions: Map<String, CanvasPoint>,
    onRootChange: (WebComponent) -> Unit,
    onPersistPosition: (String, CanvasPoint) -> Unit,
    modifier: Modifier = Modifier,
    components: List<WebSceneDoc> = emptyList(),
    excludeName: String? = null,
    instances: Map<String, String> = emptyMap(),
    onInstance: (nodeId: String, componentName: String) -> Unit = { _, _ -> },
    onOpenComponent: (componentName: String) -> Unit = {}
) {
    var selectedId by remember(root.id) { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { WebComponentTree.find(root, it) }

    // Double-click detection: the canvas only reports single selections, so two selects of the same
    // node within the window count as a double-click. Double-clicking a component-instance node opens
    // that component's implementation in its own editor tab (via onOpenComponent).
    var lastClickId by remember(root.id) { mutableStateOf<String?>(null) }
    var lastClickAt by remember(root.id) { mutableStateOf(0L) }

    // Selection sync with the Website Preview panel via the shared bus: adopt a selection published
    // elsewhere (e.g. clicking an element in the preview) only when it resolves to a node in *this*
    // scene, so other open scenes don't clobber each other.
    val busSelection by WebSelectionBus.selectedId.collectAsState()
    LaunchedEffect(busSelection, root) {
        val id = busSelection
        if (id != selectedId && (id == null || WebComponentTree.find(root, id) != null)) {
            selectedId = id
        }
    }

    Row(modifier.fillMaxSize()) {
        ComponentTreeCanvas(
            root = root,
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
            onRootChange = onRootChange,
            onPersistPosition = onPersistPosition,
            modifier = Modifier.weight(1f),
            components = components,
            excludeName = excludeName,
            instances = instances,
            onInstance = onInstance
        )
        ComponentPropertiesPanel(
            selected = selected,
            isRoot = selected?.id == root.id,
            onChange = { edited -> onRootChange(WebComponentTree.replace(root, edited.id) { edited }) },
            onDelete = {
                selected?.let { onRootChange(WebComponentTree.remove(root, it.id)) }
                selectedId = null
            }
        )
    }
}
