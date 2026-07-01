package dev.azora.website.builder.presentation.node

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.canvas.domain.model.node.AzoraNodeModel
import dev.azora.canvas.domain.type.AzoraNodeType
import dev.azora.canvas.domain.type.AzoraPortType
import dev.azora.canvas.presentation.canvas.AzoraEditorCanvas
import dev.azora.canvas.presentation.data.AzoraDrawableLink
import dev.azora.canvas.domain.model.AzoraReroutePointModel
import dev.azora.canvas.presentation.node.AzoraInputPortDef
import dev.azora.canvas.presentation.node.AzoraNode
import dev.azora.canvas.presentation.node.AzoraOutputPortDef
import dev.azora.canvas.presentation.node.InputPortsWrapper
import dev.azora.canvas.presentation.node.OutputPortsWrapper
import dev.azora.canvas.presentation.state.AzoraCanvasAction
import dev.azora.canvas.presentation.state.AzoraCanvasStateHolder
import dev.azora.sdk.core.theme.palette.AzoraPalette
import dev.azora.sdk.compiler.scene.domain.SceneReroutePoint
import dev.azora.sdk.compiler.scene.domain.randomSlotId

data class WebPort(val id: String, val label: String, val color: Color)

data class WebNode(
    val id: String,
    val position: Offset,
    val title: String,
    val subtitle: String = "",
    val accent: Color,
    val hasInput: Boolean = true,
    val outputs: List<WebPort> = emptyList(),
    /** When true, the node shows a `+` add-slot row after its output ports (container nodes). */
    val canAddSlots: Boolean = false
)

data class WebNodeLink(
    val id: String,
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val color: Color,
    /** Waypoints on this link (bend the connection); canvas-local (pre-pan) like node positions. */
    val reroutePoints: List<SceneReroutePoint> = emptyList()
)

/**
 * Website node editor over the Azora SDK canvas ([AzoraEditorCanvas]). Translates [WebNode]/
 * [WebNodeLink] into the SDK's models and forwards interaction back through plain callbacks.
 *
 * Right-click menus (empty-area, node body, output port) are supplied as [contextMenu],
 * [nodeContextMenu] and [portContextMenu] slots and rendered/anchored by the SDK — this composable
 * only forwards them and supplies per-node content.
 *
 * @param contextMenu Empty-area (right-click) menu; gets the screen-space anchor and canvas-space point.
 * @param onAddSlot Invoked when a node's `+` add-slot row is clicked (container nodes only).
 * @param nodeContextMenu Per-node right-click menu; receives the node id, screen-space anchor
 *   (canvas-local), and a dismiss callback.
 * @param portContextMenu Per-output-port right-click menu; receives the node id, port index, the
 *   screen-space anchor (canvas-local), and a dismiss callback.
 */
@Composable
fun WebNodeCanvas(
    nodes: List<WebNode>,
    links: List<WebNodeLink>,
    selectedNodeId: String?,
    onSelect: (String?) -> Unit,
    onLink: (sourceNodeId: String, sourcePortId: String, targetNodeId: String) -> Unit,
    onNodeMove: (nodeId: String, position: Offset) -> Unit,
    onNodeMoveEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contextMenu: @Composable (screenPos: Offset, worldPos: Offset, onDismiss: () -> Unit) -> Unit = { _, _, _ -> },
    onAddSlot: (nodeId: String) -> Unit = {},
    nodeContextMenu: @Composable (nodeId: String, screenPos: Offset, onDismiss: () -> Unit) -> Unit = { _, _, _ -> },
    portContextMenu: @Composable (nodeId: String, portIndex: Int, screenPos: Offset, onDismiss: () -> Unit) -> Unit = { _, _, _, _ -> },
    onRerouteAdded: (containerId: String, slotId: String, point: SceneReroutePoint, insertIndex: Int) -> Unit = { _, _, _, _ -> },
    onRerouteRemoved: (containerId: String, slotId: String, rerouteId: String) -> Unit = { _, _, _ -> },
    onRerouteMoved: (containerId: String, slotId: String, rerouteId: String, dx: Float, dy: Float) -> Unit = { _, _, _, _, _ -> }
) {
    val nodesRef = rememberUpdatedState(nodes)
    val linksRef = rememberUpdatedState(links)
    val onLinkRef = rememberUpdatedState(onLink)
    val onNodeMoveRef = rememberUpdatedState(onNodeMove)
    val onNodeMoveEndRef = rememberUpdatedState(onNodeMoveEnd)
    val onRerouteAddedRef = rememberUpdatedState(onRerouteAdded)
    val onRerouteRemovedRef = rememberUpdatedState(onRerouteRemoved)
    val onRerouteMovedRef = rememberUpdatedState(onRerouteMoved)

    val stateHolder = remember {
        AzoraCanvasStateHolder(
            onLinkCreated = { sourceId, targetId, _, outputIndex ->
                val source = nodesRef.value.firstOrNull { it.id == sourceId }
                val portId = source?.outputs?.getOrNull(outputIndex)?.id ?: return@AzoraCanvasStateHolder
                onLinkRef.value(sourceId, portId, targetId)
            },
            onNodePositionChanged = { nodeId, position -> onNodeMoveRef.value(nodeId, position) },
            onNodeDragEnded = { onNodeMoveEndRef.value() },
            // Map the SDK's link-keyed reroute callbacks onto the (containerId, slotId) of the link's
            // source slot, so the host can store waypoints on that slot. Reads linksRef so a changing
            // link list doesn't go stale inside the remembered holder.
            onReroutePointAdded = { linkId, position, insertIndex ->
                val link = linksRef.value.firstOrNull { it.id == linkId } ?: return@AzoraCanvasStateHolder
                onRerouteAddedRef.value(link.fromNodeId, link.fromPortId, SceneReroutePoint(randomSlotId(), position.x, position.y), insertIndex)
            },
            onReroutePointDeleted = { linkId, rerouteId ->
                val link = linksRef.value.firstOrNull { it.id == linkId } ?: return@AzoraCanvasStateHolder
                onRerouteRemovedRef.value(link.fromNodeId, link.fromPortId, rerouteId)
            },
            onReroutePointPositionChanged = { linkId, rerouteId, delta ->
                val link = linksRef.value.firstOrNull { it.id == linkId } ?: return@AzoraCanvasStateHolder
                onRerouteMovedRef.value(link.fromNodeId, link.fromPortId, rerouteId, delta.x, delta.y)
            }
        )
    }
    val canvasState by stateHolder.state.collectAsState()

    LaunchedEffect(selectedNodeId) {
        if (canvasState.selectedNodeId != selectedNodeId) {
            stateHolder.updateState {
                it.copy(selectedNodeId = selectedNodeId, selectedLinkId = null, selectedReroutePointId = null, selectedRerouteLinkId = null)
            }
        }
    }

    val onCanvasAction: (AzoraCanvasAction) -> Unit = { action ->
        stateHolder.onAction(action)
        when (action) {
            is AzoraCanvasAction.SelectNode -> onSelect(action.nodeId)
            is AzoraCanvasAction.ClearSelection -> onSelect(null)
            else -> {}
        }
    }

    val azoraNodes = remember(nodes) {
        nodes.map { n ->
            AzoraNodeModel(
                id = n.id, title = n.title, type = AzoraNodeType.SCENE,
                positionX = n.position.x, positionY = n.position.y, width = AzoraNodeModel.calculateWidth(n.title)
            )
        }
    }

    val inputPortPositions = remember { mutableStateMapOf<String, Offset>() }
    val outputPortPositions = remember { mutableStateMapOf<Pair<String, Int>, Offset>() }
    var canvasPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val drawableLinks = remember(nodes, links, canvasState.panOffset, canvasPositionInRoot, inputPortPositions.toMap(), outputPortPositions.toMap()) {
        links.mapNotNull { link ->
            val source = nodes.firstOrNull { it.id == link.fromNodeId } ?: return@mapNotNull null
            val target = nodes.firstOrNull { it.id == link.toNodeId } ?: return@mapNotNull null
            val outputIndex = source.outputs.indexOfFirst { it.id == link.fromPortId }.coerceAtLeast(0)
            val outOffset = outputPortPositions[source.id to outputIndex] ?: return@mapNotNull null
            val inOffset = inputPortPositions[target.id] ?: return@mapNotNull null
            AzoraDrawableLink(
                id = link.id, sourceNodeId = source.id, targetNodeId = target.id,
                startPosition = Offset(source.position.x + canvasState.panOffset.x + outOffset.x, source.position.y + canvasState.panOffset.y + outOffset.y),
                endPosition = Offset(target.position.x + canvasState.panOffset.x + inOffset.x, target.position.y + canvasState.panOffset.y + inOffset.y),
                portType = portTypeForColor(link.color), outputPortIndex = outputIndex, startColor = link.color, endColor = link.color,
                reroutePoints = link.reroutePoints.map { AzoraReroutePointModel(it.id, it.x, it.y) }
            )
        }
    }

    val linkCreationStart = run {
        val sourceId = canvasState.linkSourceNodeId
        if (!canvasState.isCreatingLink || sourceId == null) null
        else {
            val source = nodes.firstOrNull { it.id == sourceId }
            val offset = outputPortPositions[sourceId to canvasState.linkOutputPortIndex]
            if (source != null && offset != null)
                Offset(source.position.x + canvasState.panOffset.x + offset.x, source.position.y + canvasState.panOffset.y + offset.y)
            else null
        }
    }

    AzoraEditorCanvas(
        canvasState = canvasState,
        onCanvasAction = onCanvasAction,
        links = drawableLinks,
        nodes = azoraNodes,
        linkCreationStart = linkCreationStart,
        isLinkSelectable = { false },
        onCanvasPositioned = { canvasPositionInRoot = it },
        onInputPortPositioned = { nodeId, positionInRoot ->
            val node = nodes.firstOrNull { it.id == nodeId } ?: return@AzoraEditorCanvas
            inputPortPositions[nodeId] = toNodeRelative(positionInRoot, canvasPositionInRoot, node.position, canvasState.panOffset)
        },
        onOutputPortPositioned = { nodeId, index, positionInRoot ->
            val node = nodes.firstOrNull { it.id == nodeId } ?: return@AzoraEditorCanvas
            outputPortPositions[nodeId to index] = toNodeRelative(positionInRoot, canvasPositionInRoot, node.position, canvasState.panOffset)
        },
        canvasContextMenuContent = { position, onDismiss ->
            val worldPos = Offset(position.x - canvasState.panOffset.x, position.y - canvasState.panOffset.y)
            contextMenu(position, worldPos, onDismiss)
        },
        // Right-click menus are rendered/anchored by the SDK; the host only supplies the bodies.
        nodeContextMenuContent = nodeContextMenu,
        portContextMenuContent = portContextMenu,
        nodeContent = { node, isSelected, isLinkSource, _, linkTransitionType, panOffset,
                        isInputConnected, connectedOutputPortIndices, onSelectNode, onStartLink,
                        onEndLink, onMove, onEndDrag, onDismissContextMenus,
                        onInputPortPositioned, onOutputPortPositioned,
                        onContextMenu, onPortContextMenu ->
            val webNode = nodesRef.value.firstOrNull { it.id == node.id } ?: return@AzoraEditorCanvas
            AzoraNode(
                node = node, isSelected = isSelected, isLinkSource = isLinkSource, linkTransitionType = linkTransitionType,
                panOffset = panOffset, onSelect = onSelectNode, onStartLink = onStartLink, onEndLink = onEndLink,
                onMove = onMove, onEndDrag = onEndDrag, onDismissContextMenus = onDismissContextMenus,
                nodeColor = webNode.accent, borderColor = MaterialTheme.colorScheme.outlineVariant, nodeWidth = node.width.dp,
                inputPortDef = if (webNode.hasInput) AzoraInputPortDef(type = AzoraPortType.NAV_DIALOG) else null,
                outputPortDefs = webNode.outputs.mapIndexed { index, port -> AzoraOutputPortDef(index = index, label = port.label, type = portTypeForColor(port.color)) },
                isInputConnected = isInputConnected, connectedOutputPortIndices = connectedOutputPortIndices,
                onInputPortPositioned = onInputPortPositioned, onOutputPortPositioned = onOutputPortPositioned,
                // The SDK wires these to dispatch its node/port context-menu actions; forward as-is.
                onContextMenu = onContextMenu,
                onPortContextMenu = onPortContextMenu,
                canAddOutputPort = webNode.canAddSlots,
                onAddOutputPort = { onAddSlot(webNode.id) },
                headerContent = { inputPorts, outputPorts -> WebNodeBody(webNode, inputPorts, outputPorts) }
            )
        },
        modifier = modifier
    )
}

@Composable
private fun WebNodeBody(node: WebNode, inputPorts: @Composable () -> Unit, outputPorts: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(node.accent).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(node.title, color = AzoraPalette.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            InputPortsWrapper(inputPorts)
            OutputPortsWrapper(outputPorts)
        }
        if (node.subtitle.isNotEmpty()) {
            Text(node.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp))
        }
    }
}

private fun toNodeRelative(positionInRoot: Offset, canvasPositionInRoot: Offset, nodePosition: Offset, pan: Offset): Offset {
    val inCanvas = positionInRoot - canvasPositionInRoot
    return Offset(inCanvas.x - (nodePosition.x + pan.x), inCanvas.y - (nodePosition.y + pan.y))
}

private fun portTypeForColor(color: Color): AzoraPortType = when (color.copy(alpha = 1f)) {
    AzoraPalette.AccentGreen -> AzoraPortType.NAV_PUSH
    AzoraPalette.AccentOrange -> AzoraPortType.NAV_REPLACE
    AzoraPalette.AccentRed -> AzoraPortType.NAV_ROOT
    else -> AzoraPortType.NAV_DIALOG
}
