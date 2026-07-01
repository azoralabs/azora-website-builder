package dev.azora.website.builder.presentation
import dev.azora.sdk.compiler.scene.domain.SceneComponentTree
import dev.azora.sdk.compiler.scene.domain.ComponentKind

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.azora.sdk.compiler.scene.domain.SceneComponent
import dev.azora.sdk.compiler.scene.domain.SceneSpacer
import dev.azora.sdk.core.theme.palette.AzoraPalette
import dev.azora.canvas.presentation.menu.*
import dev.azora.canvas.presentation.util.autoLayout
import dev.azora.website.builder.presentation.node.*
import dev.azora.sdk.compiler.scene.domain.CanvasPoint
import dev.azora.sdk.compiler.scene.domain.SceneDocument

/**
 * Renders a scene's node pool as a node graph. Every node in [nodes] is drawn; a container's ordered
 * [dev.azora.sdk.compiler.scene.domain.SceneSlot]s are its out-ports (one port per slot, no label), and each
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
    nodes: List<SceneComponent>,
    rootId: String,
    persistedPositions: Map<String, CanvasPoint>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodesChange: (List<SceneComponent>) -> Unit,
    onPersistPosition: (String, CanvasPoint) -> Unit,
    modifier: Modifier = Modifier,
    components: List<SceneDocument> = emptyList(),
    excludeName: String? = null,
    instances: Map<String, String> = emptyMap(),
    onInstance: (nodeId: String, componentName: String) -> Unit = { _, _ -> }
) {
    val localPositions = remember { mutableStateMapOf<String, Offset>() }
    var lastDragged by remember { mutableStateOf<String?>(null) }

    val auto = remember(nodes) {
        val layout = autoLayout(nodes.size)
        nodes.mapIndexed { i, c -> c.id to (layout[i] ?: Offset.Zero) }.toMap()
    }

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
            val isContainer = SceneComponentTree.isContainer(c)
            val outputs = if (isContainer) {
                // One port per slot (id = slot id), no label. Occupied + empty slots alike.
                SceneComponentTree.slotsOf(c).map { slot -> WebPort(slot.id, "", AzoraPalette.AccentGreen) }
            } else emptyList()
            WebNode(
                id = c.id, position = pos(c.id), title = SceneComponentTree.typeLabel(c),
                subtitle = SceneComponentTree.summary(c), accent = accentFor(c), hasInput = c.id != rootId,
                outputs = outputs, canAddSlots = isContainer
            )
        }
    }

    // One link per occupied slot (a node referenced by N slots gets N incoming links → reuse).
    val links = nodes.flatMap { c ->
        if (!SceneComponentTree.isContainer(c) || instances[c.id] != null) return@flatMap emptyList()
        SceneComponentTree.slotsOf(c).mapNotNull { slot ->
            val childId = slot.childId ?: return@mapNotNull null
            if (nodes.none { it.id == childId }) return@mapNotNull null // dangling ref: nothing to draw
            WebNodeLink("${c.id}->${slot.id}", c.id, slot.id, childId, AzoraPalette.AccentGreen.copy(alpha = 0.7f), slot.reroutePoints)
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
            onNodesChange(SceneComponentTree.setSlotChild(nodes, sourceId, sourcePortId, targetId))
        },
        onNodeMove = { id, position -> localPositions[id] = position; lastDragged = id },
        onNodeMoveEnd = {
            lastDragged?.let { id ->
                localPositions[id]?.let { onPersistPosition(id, CanvasPoint(it.x, it.y)) }
                localPositions.remove(id) // clear so undo/redo can move the node via persistedPositions
            }
            lastDragged = null
        },
        onAddSlot = { containerId -> onNodesChange(SceneComponentTree.addSlot(nodes, containerId)) },
        nodeContextMenu = { nodeId, screenPos, onDismiss ->
            NodeContextMenu(
                position = screenPos,
                items = listOf(
                    NodeMenuItem("Duplicate") {
                        val (newNodes, newId) = SceneComponentTree.duplicate(nodes, nodeId)
                        if (newId != null) {
                            onNodesChange(newNodes)
                            // Place the copy just off the original so it's visible immediately.
                            val offset = pos(nodeId).let { Offset(it.x + 40f, it.y + 40f) }
                            localPositions[newId] = offset
                            onPersistPosition(newId, CanvasPoint(offset.x, offset.y))
                            onSelect(newId)
                        }
                        onDismiss()
                    },
                    NodeMenuItem("Delete node", color = AzoraPalette.AccentRed) {
                        onNodesChange(SceneComponentTree.removeNode(nodes, nodeId))
                        if (selectedId == nodeId) onSelect(null)
                        onDismiss()
                    }
                ),
                onDismiss = onDismiss
            )
        },
        onRerouteAdded = { containerId, slotId, point, insertIndex ->
            onNodesChange(SceneComponentTree.addReroute(nodes, containerId, slotId, point, insertIndex))
        },
        onRerouteRemoved = { containerId, slotId, rerouteId ->
            onNodesChange(SceneComponentTree.removeReroute(nodes, containerId, slotId, rerouteId))
        },
        onRerouteMoved = { containerId, slotId, rerouteId, dx, dy ->
            onNodesChange(SceneComponentTree.moveReroute(nodes, containerId, slotId, rerouteId, dx, dy))
        },
        portContextMenu = { nodeId, portIndex, screenPos, onDismiss ->
            val container = SceneComponentTree.byId(nodes, nodeId)
            val slot = container?.let { SceneComponentTree.slotsOf(it).getOrNull(portIndex) }
            if (slot == null) { onDismiss(); return@WebNodeCanvas }
            NodeContextMenu(
                position = screenPos,
                items = listOf(
                    NodeMenuItem("Delete", color = AzoraPalette.AccentRed) {
                        // Removes just this slot; the referenced node stays in the pool (may still be
                        // referenced elsewhere, or becomes free-floating). Others keep their order.
                        onNodesChange(SceneComponentTree.removeSlot(nodes, nodeId, slot.id))
                        onDismiss()
                    }
                ),
                onDismiss = onDismiss
            )
        },
        contextMenu = { screenPos, worldPos, onDismiss ->
            // New nodes are created free-floating (added to the pool, referenced by nothing) — wired
            // into a container later by dragging a slot onto them.
            fun insert(created: SceneComponent) {
                localPositions[created.id] = worldPos
                onPersistPosition(created.id, CanvasPoint(worldPos.x, worldPos.y))
                onNodesChange(SceneComponentTree.addNode(nodes, created))
                onSelect(created.id)
            }
            val menuComponents = components.filter { it.name != excludeName }
            val sections = buildList {
                add(NodeMenuSection("Add element", ComponentKind.entries.map { kind -> NodeMenuItem(kind.label) { insert(SceneComponentTree.create(kind)) } }))
                if (menuComponents.isNotEmpty()) {
                    add(NodeMenuSection("Components", menuComponents.map { def ->
                        NodeMenuItem(def.name) {
                            val anchor = SceneSpacer()
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
private fun accentFor(c: SceneComponent): Color = when (SceneComponentTree.typeLabel(c)) {
    "Column", "Row", "Box" -> AzoraPalette.AccentBlue
    "Text" -> AzoraPalette.AccentTeal
    "Button" -> AzoraPalette.AccentGreen
    "Link" -> AzoraPalette.AccentPurple
    "Image" -> AzoraPalette.AccentOrange
    "Input" -> AzoraPalette.AccentPink
    else -> AzoraPalette.Neutral45
}
