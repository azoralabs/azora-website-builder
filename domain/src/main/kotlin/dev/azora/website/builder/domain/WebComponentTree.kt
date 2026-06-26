package dev.azora.website.builder.domain

import dev.azora.website.builder.domain.*

/**
 * Generic, immutable operations over a [WebComponent] tree, used by the `.azscene` editors.
 * Only [WebColumn]/[WebRow]/[WebBox] hold children; every other variant is a leaf.
 */
object WebComponentTree {

    fun childrenOf(c: WebComponent): List<WebComponent> = when (c) {
        is WebColumn -> c.children
        is WebRow -> c.children
        is WebBox -> c.children
        else -> emptyList()
    }

    fun isContainer(c: WebComponent): Boolean = c is WebColumn || c is WebRow || c is WebBox

    fun withChildren(c: WebComponent, children: List<WebComponent>): WebComponent = when (c) {
        is WebColumn -> c.copy(children = children)
        is WebRow -> c.copy(children = children)
        is WebBox -> c.copy(children = children)
        else -> c
    }

    fun find(root: WebComponent, id: String): WebComponent? {
        if (root.id == id) return root
        for (child in childrenOf(root)) find(child, id)?.let { return it }
        return null
    }

    fun replace(root: WebComponent, id: String, transform: (WebComponent) -> WebComponent): WebComponent {
        if (root.id == id) return transform(root)
        return withChildren(root, childrenOf(root).map { replace(it, id, transform) })
    }

    fun remove(root: WebComponent, id: String): WebComponent {
        val kept = childrenOf(root).filter { it.id != id }.map { remove(it, id) }
        return withChildren(root, kept)
    }

    fun addChild(root: WebComponent, parentId: String, child: WebComponent): WebComponent =
        replace(root, parentId) { node -> if (isContainer(node)) withChildren(node, childrenOf(node) + child) else node }

    fun reparent(root: WebComponent, id: String, newParentId: String): WebComponent {
        if (id == newParentId) return root
        val moving = find(root, id) ?: return root
        if (find(moving, newParentId) != null) return root
        val target = find(root, newParentId) ?: return root
        if (!isContainer(target)) return root
        return addChild(remove(root, id), newParentId, moving)
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
        is WebColumn -> "${c.children.size} child(ren)"
        is WebRow -> "${c.children.size} child(ren)"
        is WebBox -> "${c.children.size} child(ren)"
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
