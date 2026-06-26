package dev.azora.website.builder.presentation
import dev.azora.website.builder.domain.WebComponentTree
import dev.azora.website.builder.domain.ComponentKind

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.azora.website.builder.domain.WebComponent
import dev.azora.website.builder.domain.WebSpacer
import dev.azora.sdk.core.theme.palette.AzoraPalette
import dev.azora.website.builder.presentation.node.*
import dev.azora.website.builder.domain.CanvasPoint
import dev.azora.website.builder.domain.WebSceneDoc

/**
 * Renders a [WebComponent] tree as a node graph: every component is a node; a container's `children`
 * output links to each child's input. Drag to re-parent; right-click adds an element or inserts a
 * **component instance** (a single anchor node referencing a component by name, recorded via
 * [onInstance] and expanded at generation time).
 */
@Composable
fun ComponentTreeCanvas(
    root: WebComponent,
    persistedPositions: Map<String, CanvasPoint>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onRootChange: (WebComponent) -> Unit,
    onPersistPosition: (String, CanvasPoint) -> Unit,
    modifier: Modifier = Modifier,
    components: List<WebSceneDoc> = emptyList(),
    excludeName: String? = null,
    instances: Map<String, String> = emptyMap(),
    onInstance: (nodeId: String, componentName: String) -> Unit = { _, _ -> }
) {
    val localPositions = remember { mutableStateMapOf<String, Offset>() }
    var lastDragged by remember { mutableStateOf<String?>(null) }

    val flat = remember(root) { flatten(root) }
    val auto = remember(root) { autoLayout(flat) }

    fun pos(id: String): Offset =
        localPositions[id] ?: persistedPositions[id]?.let { Offset(it.x, it.y) } ?: auto[id] ?: Offset.Zero

    val nodes = flat.map { entry ->
        val c = entry.component
        val instanceName = instances[c.id]
        if (instanceName != null) {
            WebNode(
                id = c.id, position = pos(c.id), title = instanceName, subtitle = "component",
                accent = AzoraPalette.AccentPurple, hasInput = entry.parentId != null, outputs = emptyList()
            )
        } else {
            WebNode(
                id = c.id, position = pos(c.id), title = WebComponentTree.typeLabel(c),
                subtitle = WebComponentTree.summary(c), accent = accentFor(c), hasInput = entry.parentId != null,
                outputs = if (WebComponentTree.isContainer(c)) listOf(WebPort("children", "children", AzoraPalette.AccentGreen)) else emptyList()
            )
        }
    }

    val links = flat.flatMap { entry ->
        WebComponentTree.childrenOf(entry.component).map { child ->
            WebNodeLink("${entry.component.id}->${child.id}", entry.component.id, "children", child.id, AzoraPalette.AccentGreen.copy(alpha = 0.7f))
        }
    }

    WebNodeCanvas(
        modifier = modifier,
        nodes = nodes,
        links = links,
        selectedNodeId = selectedId,
        onSelect = { onSelect(it) },
        onLink = { sourceId, _, targetId -> if (sourceId != targetId) onRootChange(WebComponentTree.reparent(root, targetId, sourceId)) },
        onNodeMove = { id, position -> localPositions[id] = position; lastDragged = id },
        onNodeMoveEnd = { lastDragged?.let { id -> localPositions[id]?.let { onPersistPosition(id, CanvasPoint(it.x, it.y)) } }; lastDragged = null },
        contextMenu = { screenPos, worldPos, onDismiss ->
            val parentId = selectedId?.let { WebComponentTree.find(root, it) }?.takeIf { WebComponentTree.isContainer(it) }?.id ?: root.id
            fun insert(created: WebComponent) {
                localPositions[created.id] = worldPos
                onPersistPosition(created.id, CanvasPoint(worldPos.x, worldPos.y))
                onRootChange(WebComponentTree.addChild(root, parentId, created))
                onSelect(created.id)
            }
            val menuComponents = components.filter { it.name != excludeName }
            val sections = buildList {
                add(NodeMenuSection("Add element", ComponentKind.entries.map { kind -> NodeMenuItem(kind.label) { insert(WebComponentTree.create(kind)) } }))
                if (menuComponents.isNotEmpty()) {
                    add(NodeMenuSection("Components", menuComponents.map { def ->
                        NodeMenuItem(def.name) {
                            val anchor = WebSpacer()
                            onInstance(anchor.id, def.name)
                            insert(anchor)
                        }
                    }))
                }
            }
            NodeContextMenu(position = screenPos, sections = sections, onDismiss = onDismiss)
        }
    )
}

private data class FlatNode(val component: WebComponent, val depth: Int, val parentId: String?)

private fun flatten(root: WebComponent): List<FlatNode> {
    val out = mutableListOf<FlatNode>()
    fun visit(c: WebComponent, depth: Int, parentId: String?) {
        out += FlatNode(c, depth, parentId)
        WebComponentTree.childrenOf(c).forEach { visit(it, depth + 1, c.id) }
    }
    visit(root, 0, null)
    return out
}

private fun autoLayout(flat: List<FlatNode>): Map<String, Offset> {
    val result = LinkedHashMap<String, Offset>()
    var row = 0
    flat.forEach { entry -> result[entry.component.id] = Offset(40f + entry.depth * 250f, 40f + row * 84f); row++ }
    return result
}

private fun accentFor(c: WebComponent): Color = when (WebComponentTree.typeLabel(c)) {
    "Column", "Row", "Box" -> AzoraPalette.AccentBlue
    "Text" -> AzoraPalette.AccentTeal
    "Button" -> AzoraPalette.AccentGreen
    "Link" -> AzoraPalette.AccentPurple
    "Image" -> AzoraPalette.AccentOrange
    "Input" -> AzoraPalette.AccentPink
    else -> AzoraPalette.Neutral45
}
