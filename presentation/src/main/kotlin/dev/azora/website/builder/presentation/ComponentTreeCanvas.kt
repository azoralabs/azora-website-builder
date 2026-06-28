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
 * Renders a scene's node pool as a node graph. Every node in [nodes] is drawn; a container's ordered
 * [dev.azora.website.builder.domain.WebSlot]s are its out-ports (one port per slot, no label), and each
 * occupied slot links to the node it references — so a node reused in several slots shows several
 * incoming links. The `+` row appends an empty slot; right-clicking a slot deletes it (others keep
 * their order). Dragging a slot onto a node sets that slot's reference (the node isn't moved, so it
 * can be referenced again elsewhere). Newly created nodes are free-floating (in the pool, referenced
 * by nothing) — they reach the generator/preview only once a slot references them.
 *
 * Right-clicking empty canvas adds an element or inserts a **component instance** (a single anchor
 * node referencing a component by name, recorded via [onInstance] and expanded at generation time).
 */
@Composable
fun ComponentTreeCanvas(
    nodes: List<WebComponent>,
    rootId: String,
    persistedPositions: Map<String, CanvasPoint>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodesChange: (List<WebComponent>) -> Unit,
    onPersistPosition: (String, CanvasPoint) -> Unit,
    modifier: Modifier = Modifier,
    components: List<WebSceneDoc> = emptyList(),
    excludeName: String? = null,
    instances: Map<String, String> = emptyMap(),
    onInstance: (nodeId: String, componentName: String) -> Unit = { _, _ -> }
) {
    val localPositions = remember { mutableStateMapOf<String, Offset>() }
    var lastDragged by remember { mutableStateOf<String?>(null) }

    val auto = remember(nodes) { autoLayout(nodes) }

    fun pos(id: String): Offset =
        localPositions[id] ?: persistedPositions[id]?.let { Offset(it.x, it.y) } ?: auto[id] ?: Offset.Zero

    val webNodes = nodes.map { c ->
        val instanceName = instances[c.id]
        if (instanceName != null) {
            WebNode(
                id = c.id, position = pos(c.id), title = instanceName, subtitle = "component",
                accent = AzoraPalette.AccentPurple, hasInput = c.id != rootId, outputs = emptyList(),
                canAddSlots = false
            )
        } else {
            val isContainer = WebComponentTree.isContainer(c)
            val outputs = if (isContainer) {
                // One port per slot (id = slot id), no label. Occupied + empty slots alike.
                WebComponentTree.slotsOf(c).map { slot -> WebPort(slot.id, "", AzoraPalette.AccentGreen) }
            } else emptyList()
            WebNode(
                id = c.id, position = pos(c.id), title = WebComponentTree.typeLabel(c),
                subtitle = WebComponentTree.summary(c), accent = accentFor(c), hasInput = c.id != rootId,
                outputs = outputs, canAddSlots = isContainer
            )
        }
    }

    // One link per occupied slot (a node referenced by N slots gets N incoming links → reuse).
    val links = nodes.flatMap { c ->
        if (!WebComponentTree.isContainer(c) || instances[c.id] != null) return@flatMap emptyList()
        WebComponentTree.slotsOf(c).mapNotNull { slot ->
            val childId = slot.childId ?: return@mapNotNull null
            if (nodes.none { it.id == childId }) return@mapNotNull null // dangling ref: nothing to draw
            WebNodeLink("${c.id}->${slot.id}", c.id, slot.id, childId, AzoraPalette.AccentGreen.copy(alpha = 0.7f))
        }
    }

    WebNodeCanvas(
        modifier = modifier,
        nodes = webNodes,
        links = links,
        selectedNodeId = selectedId,
        onSelect = { onSelect(it) },
        onLink = { sourceId, sourcePortId, targetId ->
            // Dragging a slot onto a node sets that slot's reference (node stays in the pool, so it can
            // be referenced by other slots too → multi-reference).
            onNodesChange(WebComponentTree.setSlotChild(nodes, sourceId, sourcePortId, targetId))
        },
        onNodeMove = { id, position -> localPositions[id] = position; lastDragged = id },
        onNodeMoveEnd = { lastDragged?.let { id -> localPositions[id]?.let { onPersistPosition(id, CanvasPoint(it.x, it.y)) } }; lastDragged = null },
        onAddSlot = { containerId -> onNodesChange(WebComponentTree.addSlot(nodes, containerId)) },
        nodeContextMenu = { nodeId, screenPos, onDismiss ->
            NodeContextMenu(
                position = screenPos,
                items = listOf(
                    NodeMenuItem("Delete node", color = AzoraPalette.AccentRed) {
                        onNodesChange(WebComponentTree.removeNode(nodes, nodeId))
                        if (selectedId == nodeId) onSelect(null)
                        onDismiss()
                    }
                ),
                onDismiss = onDismiss
            )
        },
        portContextMenu = { nodeId, portIndex, screenPos, onDismiss ->
            val container = WebComponentTree.byId(nodes, nodeId)
            val slot = container?.let { WebComponentTree.slotsOf(it).getOrNull(portIndex) }
            if (slot == null) { onDismiss(); return@WebNodeCanvas }
            NodeContextMenu(
                position = screenPos,
                items = listOf(
                    NodeMenuItem("Delete", color = AzoraPalette.AccentRed) {
                        // Removes just this slot; the referenced node stays in the pool (may still be
                        // referenced elsewhere, or becomes free-floating). Others keep their order.
                        onNodesChange(WebComponentTree.removeSlot(nodes, nodeId, slot.id))
                        onDismiss()
                    }
                ),
                onDismiss = onDismiss
            )
        },
        contextMenu = { screenPos, worldPos, onDismiss ->
            // New nodes are created free-floating (added to the pool, referenced by nothing) — wired
            // into a container later by dragging a slot onto them.
            fun insert(created: WebComponent) {
                localPositions[created.id] = worldPos
                onPersistPosition(created.id, CanvasPoint(worldPos.x, worldPos.y))
                onNodesChange(WebComponentTree.addNode(nodes, created))
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

/** Stagger pool nodes in a grid as a fallback position; persisted/click positions always win. */
private fun autoLayout(nodes: List<WebComponent>): Map<String, Offset> {
    val result = LinkedHashMap<String, Offset>()
    nodes.forEachIndexed { i, c ->
        result[c.id] = Offset(40f + (i % 4) * 240f, 40f + (i / 4) * 100f)
    }
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
