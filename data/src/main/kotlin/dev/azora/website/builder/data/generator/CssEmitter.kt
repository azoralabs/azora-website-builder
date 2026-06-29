package dev.azora.website.builder.data.generator

import dev.azora.sdk.core.project.domain.CodeGenerator.GenScope
import dev.azora.website.builder.domain.*

/**
 * Emits plain CSS for a page/component tree, one rule per node keyed by [cssClass]. Containers map to
 * flexbox; arrangement maps to `justify-content`/`align-items`. Component-instance anchors contribute
 * no rules. Output is produced through the SDK [dev.azora.sdk.core.project.domain.CodeGenerator].
 */
object CssEmitter {

    /** Stable CSS class for a node id (sanitized so it is always a valid identifier). */
    fun cssClass(id: String): String =
        "n_" + id.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")

    /** Full stylesheet for one page/component: walks nodes reachable from [rootId] via slots,
     *  emitting one rule per node (a reused node referenced by several slots still gets a single rule;
     *  the global `visited` set also breaks cycles). Instance anchors contribute no rules. */
    fun fileCss(rootId: String, nodes: List<WebComponent>, instances: Map<String, String>): String {
        val pool = nodes.associateBy { it.id }
        val root = pool[rootId] ?: return ""
        val visited = mutableSetOf<String>()
        return buildSource { emitReachable(this, root, pool, instances, visited) }
    }

    private fun emitReachable(
        scope: GenScope,
        node: WebComponent,
        pool: Map<String, WebComponent>,
        instances: Map<String, String>,
        visited: MutableSet<String>
    ) {
        if (!visited.add(node.id)) return // already emitted (dedup + cycle break)
        if (instances.containsKey(node.id)) return // instance anchor: the referenced component styles itself
        val decls = declarations(node)
        if (decls.isNotEmpty()) {
            scope.write("${selectorFor(node)} {")
            scope.gen { decls.forEach { write("$it;") } }
            scope.write("}")
            scope.blank()
        }
        when (node) {
            is WebColumn, is WebRow, is WebBox -> slotChildIds(node).forEach { cid ->
                pool[cid]?.let { emitReachable(scope, it, pool, instances, visited) }
            }
            else -> {}
        }
    }

    private fun slotChildIds(c: WebComponent): List<String> = when (c) {
        is WebColumn -> c.slots.mapNotNull { it.childId }
        is WebRow -> c.slots.mapNotNull { it.childId }
        is WebBox -> c.slots.mapNotNull { it.childId }
        else -> emptyList()
    }

    /** Per-node CSS selector. A button also carries the `.az-button` base class (global `index.css`),
     *  which sets `height`/`padding`/`border`/`background`/… — the very properties modifiers control.
     *  Qualifying the selector with `.az-button` lifts it to specificity (0,2,0), so the node's own
     *  rule always beats `.az-button` (0,1,0) regardless of stylesheet load order; properties the
     *  node doesn't set still fall through to the `.az-button` brand default. */
    private fun selectorFor(node: WebComponent): String {
        val cls = cssClass(node.id)
        return if (node is WebButton) ".az-button.$cls" else ".$cls"
    }

    private fun declarations(c: WebComponent): List<String> {
        val m = c.modifier
        val out = mutableListOf<String>()
        when (c) {
            is WebColumn -> {
                out += "display: flex"; out += "flex-direction: column"
                out += "justify-content: ${justify(c.arrangement)}"
                out += "align-items: ${if (c.arrangement == WebArrangement.CENTER) "center" else "flex-start"}"
            }
            is WebRow -> {
                out += "display: flex"; out += "flex-direction: row"
                out += "justify-content: ${justify(c.arrangement)}"
                out += "align-items: ${if (c.arrangement == WebArrangement.CENTER) "center" else "flex-start"}"
            }
            is WebBox -> { out += "display: flex"; out += "flex-direction: column" }
            // Text/Link render as <span>/<a> (inline by default); make them inline-block so their box
            // modifiers (width/height/fill/padding/text-align/border/cornerRadius) actually apply in
            // the browser — matching the Compose preview, which renders them as modifier-bearing boxes.
            is WebText -> { out += "display: inline-block" }
            is WebLink -> { out += "display: inline-block" }
            else -> {}
        }
        if (m.fillMaxWidth) out += "width: 100%"
        if (m.fillMaxHeight) out += "height: 100%"
        m.width?.let { out += "width: ${it}px" }
        m.height?.let { out += "height: ${it}px" }
        m.padding?.let { out += "padding: ${it}px" }
        m.gap?.let { out += "gap: ${it}px" }
        color(m.backgroundColor)?.let { out += "background-color: $it" }
        color(m.textColor)?.let { out += "color: $it" }
        m.fontSize?.let { out += "font-size: ${it}px" }
        if (m.fontWeight != WebFontWeight.NORMAL) out += "font-weight: ${fontWeight(m.fontWeight)}"
        if (m.textAlign != WebTextAlign.START) out += "text-align: ${textAlign(m.textAlign)}"
        // Border radius — per-corner elliptical ([WebModifier.corners]) or legacy uniform ([WebModifier.cornerRadius]).
        val corners = m.corners ?: m.cornerRadius?.let { WebCornerRadius.uniform(it) }
        if (corners != null && !corners.isZero()) {
            out += "border-radius: ${corners.topLeft.x}px ${corners.topRight.x}px ${corners.bottomRight.x}px ${corners.bottomLeft.x}px" +
                " / ${corners.topLeft.y}px ${corners.topRight.y}px ${corners.bottomRight.y}px ${corners.bottomLeft.y}px"
        }
        // Border — drawn inside/outside/center relative to the box edge.
        m.borderWidth?.let { w ->
            val c = color(m.borderColor) ?: "#000000"
            when (m.borderPosition) {
                WebBorderPosition.INSIDE -> { out += "box-sizing: border-box"; out += "border: ${w}px solid $c" }
                WebBorderPosition.OUTSIDE -> { out += "box-sizing: content-box"; out += "border: ${w}px solid $c" }
                WebBorderPosition.CENTER -> {
                    val inner = (w + 1) / 2 // ceil(w/2): inside half
                    val outer = w / 2        // floor(w/2): outside half
                    out += "box-shadow: inset 0 0 0 ${inner}px $c, 0 0 0 ${outer}px $c"
                }
            }
        }
        m.opacity?.let { out += "opacity: ${(it.coerceIn(0, 100)) / 100.0}" }
        return out
    }

    private fun justify(a: WebArrangement): String = when (a) {
        WebArrangement.START -> "flex-start"
        WebArrangement.CENTER -> "center"
        WebArrangement.END -> "flex-end"
        WebArrangement.SPACE_BETWEEN -> "space-between"
    }

    private fun fontWeight(w: WebFontWeight): Int = when (w) {
        WebFontWeight.NORMAL -> 400
        WebFontWeight.MEDIUM -> 500
        WebFontWeight.SEMI_BOLD -> 600
        WebFontWeight.BOLD -> 700
    }

    private fun textAlign(a: WebTextAlign): String = when (a) {
        WebTextAlign.START -> "left"
        WebTextAlign.CENTER -> "center"
        WebTextAlign.END -> "right"
    }

    /** Normalizes a `#RGB`/`#RRGGBB` hex string to `#rrggbb`, or null if unparseable. */
    fun color(hex: String?): String? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.removePrefix("#").trim()
        val rgb = when (cleaned.length) {
            6 -> cleaned
            3 -> cleaned.map { "$it$it" }.joinToString("")
            else -> return null
        }
        if (rgb.any { it.digitToIntOrNull(16) == null }) return null
        return "#$rgb"
    }
}
