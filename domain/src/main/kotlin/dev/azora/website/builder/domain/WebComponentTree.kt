package dev.azora.website.builder.domain

import dev.azora.canvas.domain.AzoraNodeSlot
import dev.azora.canvas.domain.AzoraSlotGraph
import dev.azora.canvas.domain.AzoraSlotNodeAdapter

/**
 * Operations over a scene's flat node pool ([WebSceneDoc.nodes]). The pool/slot algorithms (resolve,
 * set/add/remove slot, add/remove node, reachable traversal) delegate to the generic SDK
 * [AzoraSlotGraph]; this object only contributes the [WebComponent]-specific bits: which variants are
 * containers, how to read/replace their [WebSlot]s, type labels, and the palette factory.
 *
 * Containers reference children by id through ordered slots, so the same node may be referenced by
 * several slots. A node is "free-floating" when no slot reachable from the root references it.
 */
object WebComponentTree {

    /** Bridges the generic [AzoraSlotGraph] to [WebComponent] (maps [WebSlot] ↔ [AzoraNodeSlot]).
     *  [withSlots] merges by slot id so slot-only edits preserve each slot's reroutePoints (the generic
     *  [AzoraNodeSlot] carries only id/childId). */
    private val slotAdapter = object : AzoraSlotNodeAdapter<WebComponent> {
        override fun id(node: WebComponent): String = node.id
        override fun slots(node: WebComponent): List<AzoraNodeSlot> =
            slotsOf(node).map { AzoraNodeSlot(it.id, it.childId) }
        override fun isContainer(node: WebComponent): Boolean = this@WebComponentTree.isContainer(node)
        override fun withSlots(node: WebComponent, slots: List<AzoraNodeSlot>): WebComponent {
            val byId = slotsOf(node).associateBy { it.id }
            return this@WebComponentTree.withSlots(node, slots.map { s ->
                WebSlot(s.id, s.childId, byId[s.id]?.reroutePoints ?: emptyList())
            })
        }
        override fun newSlotId(): String = randomSlotId()
        override fun withId(node: WebComponent, id: String): WebComponent = node.withNodeId(id)
        override fun newNodeId(): String = randomComponentId()
    }

    /** Copy of [this] with a different id (used by duplication). */
    private fun WebComponent.withNodeId(newId: String): WebComponent = when (this) {
        is WebColumn -> copy(id = newId); is WebRow -> copy(id = newId); is WebBox -> copy(id = newId)
        is WebText -> copy(id = newId); is WebButton -> copy(id = newId); is WebImage -> copy(id = newId)
        is WebLink -> copy(id = newId); is WebInput -> copy(id = newId); is WebSpacer -> copy(id = newId)
    }

    private fun graph(nodes: List<WebComponent>): AzoraSlotGraph<WebComponent> = AzoraSlotGraph(nodes, slotAdapter)

    fun byId(nodes: List<WebComponent>, id: String): WebComponent? = graph(nodes).node(id)

    fun isContainer(c: WebComponent): Boolean = c is WebColumn || c is WebRow || c is WebBox

    fun slotsOf(c: WebComponent): List<WebSlot> = when (c) {
        is WebColumn -> c.slots
        is WebRow -> c.slots
        is WebBox -> c.slots
        else -> emptyList()
    }

    fun withSlots(c: WebComponent, slots: List<WebSlot>): WebComponent = when (c) {
        is WebColumn -> c.copy(slots = slots)
        is WebRow -> c.copy(slots = slots)
        is WebBox -> c.copy(slots = slots)
        else -> c
    }

    /** Replaces the node with `id` via [transform], leaving the rest of the pool untouched. */
    fun replaceNode(nodes: List<WebComponent>, id: String, transform: (WebComponent) -> WebComponent): List<WebComponent> =
        graph(nodes).replaceNode(id, transform).nodes

    /** Wires slot `slotId` on container `containerId` to reference `childId` (the core of drag-connect
     *  and reuse: the node isn't moved, so other slots can still reference it). */
    fun setSlotChild(nodes: List<WebComponent>, containerId: String, slotId: String, childId: String?): List<WebComponent> =
        graph(nodes).setSlotChild(containerId, slotId, childId).nodes

    /** Appends a new empty slot to container `containerId` (the `+` affordance). */
    fun addSlot(nodes: List<WebComponent>, containerId: String): List<WebComponent> =
        graph(nodes).addSlot(containerId).nodes

    /** Removes slot `slotId` from container `containerId` (right-click delete; remaining slots keep order). */
    fun removeSlot(nodes: List<WebComponent>, containerId: String, slotId: String): List<WebComponent> =
        graph(nodes).removeSlot(containerId, slotId).nodes

    /** Appends a new node to the pool (free-floating until a slot references it). */
    fun addNode(nodes: List<WebComponent>, node: WebComponent): List<WebComponent> =
        graph(nodes).addNode(node).nodes

    /** Removes node `id` from the pool and clears any slot references pointing at it (they become empty). */
    fun removeNode(nodes: List<WebComponent>, id: String): List<WebComponent> =
        graph(nodes).removeNode(id).nodes

    /** Resolves a container's slots to their referenced nodes in pool order (duplicates kept, so a node
     *  referenced by N slots appears N times). Missing refs are skipped. */
    fun slotChildren(nodes: List<WebComponent>, c: WebComponent): List<WebComponent> =
        graph(nodes).slotChildren(c)

    /** Every node reachable from [rootId] via slots, each once (deduped, cycle-safe). */
    fun reachableFrom(nodes: List<WebComponent>, rootId: String): List<WebComponent> =
        graph(nodes).reachableFrom(rootId)

    /** Duplicates node [id] (same content; a container copy shares the original's children) into a
     *  free-floating pool node. Returns the new pool + the new node's id (null if [id] not found). */
    fun duplicate(nodes: List<WebComponent>, id: String): Pair<List<WebComponent>, String?> {
        val (g, newId) = graph(nodes).duplicate(id)
        return g.nodes to newId
    }

    // --- Reroute points (waypoints on a slot's link) — web-specific, live on the slot. ---

    fun addReroute(
        nodes: List<WebComponent>, containerId: String, slotId: String,
        point: WebReroutePoint, insertIndex: Int
    ): List<WebComponent> = replaceNode(nodes, containerId) { c ->
        if (!isContainer(c)) c else withSlots(c, slotsOf(c).map { s ->
            if (s.id != slotId) s else {
                val pts = s.reroutePoints.toMutableList()
                pts.add(insertIndex.coerceIn(0, pts.size), point)
                s.copy(reroutePoints = pts)
            }
        })
    }

    fun removeReroute(nodes: List<WebComponent>, containerId: String, slotId: String, rerouteId: String): List<WebComponent> =
        replaceNode(nodes, containerId) { c ->
            if (!isContainer(c)) c else withSlots(c, slotsOf(c).map { s ->
                if (s.id != slotId) s else s.copy(reroutePoints = s.reroutePoints.filterNot { it.id == rerouteId })
            })
        }

    fun moveReroute(
        nodes: List<WebComponent>, containerId: String, slotId: String,
        rerouteId: String, dx: Float, dy: Float
    ): List<WebComponent> = replaceNode(nodes, containerId) { c ->
        if (!isContainer(c)) c else withSlots(c, slotsOf(c).map { s ->
            if (s.id != slotId) s else s.copy(reroutePoints = s.reroutePoints.map { p ->
                if (p.id != rerouteId) p else p.copy(x = p.x + dx, y = p.y + dy)
            })
        })
    }

    fun typeLabel(c: WebComponent): String = when (c) {
        is WebColumn -> "Column"
        is WebRow -> "Row"
        is WebBox -> "Box"
        is WebText -> "Text"
        is WebButton -> "Button"
        is WebImage -> "Image"
        is WebLink -> "Link"
        is WebInput -> "Input"
        is WebSpacer -> "Spacer"
    }

    fun summary(c: WebComponent): String = when (c) {
        is WebText -> c.text
        is WebButton -> c.label
        is WebLink -> "${c.text} → ${c.href}"
        is WebImage -> c.alt.ifBlank { c.src }
        is WebInput -> c.placeholder
        is WebColumn -> "${slotsOf(c).count { it.childId != null }} child(ren)"
        is WebRow -> "${slotsOf(c).count { it.childId != null }} child(ren)"
        is WebBox -> "${slotsOf(c).count { it.childId != null }} child(ren)"
        is WebSpacer -> ""
    }

    fun create(kind: ComponentKind): WebComponent = when (kind) {
        ComponentKind.COLUMN -> WebColumn()
        ComponentKind.ROW -> WebRow()
        ComponentKind.BOX -> WebBox()
        ComponentKind.TEXT -> WebText(text = "Text")
        ComponentKind.BUTTON -> WebButton(label = "Button")
        ComponentKind.IMAGE -> WebImage(src = "", alt = "image")
        ComponentKind.LINK -> WebLink(text = "Link", href = "/")
        ComponentKind.INPUT -> WebInput(placeholder = "Enter…")
        ComponentKind.SPACER -> WebSpacer()
    }
}

/** The palette of component types that can be added on a tree canvas. */
enum class ComponentKind(val label: String) {
    COLUMN("Column"), ROW("Row"), BOX("Box"), TEXT("Text"),
    BUTTON("Button"), IMAGE("Image"), LINK("Link"), INPUT("Input"), SPACER("Spacer")
}
