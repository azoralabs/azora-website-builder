package dev.azora.website.builder.data.generator

import dev.azora.sdk.compiler.scene.domain.SceneArrangement
import dev.azora.sdk.compiler.scene.domain.SceneBorderPosition
import dev.azora.sdk.compiler.scene.domain.SceneButton
import dev.azora.sdk.compiler.scene.domain.SceneBox
import dev.azora.sdk.compiler.scene.domain.SceneColumn
import dev.azora.sdk.compiler.scene.domain.SceneCorner
import dev.azora.sdk.compiler.scene.domain.SceneCornerRadius
import dev.azora.sdk.compiler.scene.domain.SceneFontWeight
import dev.azora.sdk.compiler.scene.domain.SceneModifier
import dev.azora.sdk.compiler.scene.domain.SceneSlot
import dev.azora.sdk.compiler.scene.domain.SceneText
import dev.azora.sdk.compiler.scene.domain.SceneTextAlign
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

/**
 * Regression tests for modifier → CSS emission. Guard against the button's per-node styles losing
 * to the global `.az-button` base (size/border silently dropped) and the inline `<span>`/`<a>`
 * box-modifier gap.
 */
class CssEmitterTest {

    @Test
    fun `button height and border win over az-button base via boosted selector`() {
        val btn = SceneButton(
            id = "c_abc",
            label = "Go",
            modifier = SceneModifier(height = 50, borderWidth = 2, borderColor = "#000000")
        )
        val css = CssEmitter.fileCss(btn.id, listOf(btn), emptyMap())

        // The per-node rule must be qualified with `.az-button` (specificity 0,2,0) so it beats the
        // global `.az-button` (0,1,0) regardless of stylesheet order.
        assertTrue(".az-button.n_c_abc {" in css, "expected boosted selector `.az-button.n_c_abc`, got:\n$css")
        assertTrue("height: 50px" in css, "height not emitted, got:\n$css")
        assertTrue("border: 2px solid #000000" in css, "border not emitted, got:\n$css")
    }

    @Test
    fun `button jsx carries the per-node className that the css targets`() {
        val btn = SceneButton(id = "c_abc", label = "Go")
        val jsx = buildSource { JsxEmitter.emit(this, btn, mapOf(btn.id to btn), emptyMap(), emptyMap()) }
        // AzButton merges this className with `az-button`, so `.az-button.n_c_abc` matches the element.
        assertTrue("<AzButton className=\"n_c_abc\">" in jsx, "button JSX must carry its class, got:\n$jsx")
    }

    @Test
    fun `non-button nodes use the plain per-node selector`() {
        val text = SceneText(id = "c_t", text = "hi", modifier = SceneModifier(width = 100))
        val col = SceneColumn(
            id = "c_root",
            modifier = SceneModifier(fillMaxWidth = true, padding = 16),
            slots = listOf(SceneSlot(id = "s1", childId = "c_t"))
        )
        val css = CssEmitter.fileCss(col.id, listOf(col, text), emptyMap())

        assertTrue(".n_c_root {" in css, "container selector, got:\n$css")
        assertTrue(".n_c_t {" in css, "text selector, got:\n$css")
        assertFalse(".az-button.n_c_root" in css, "non-button must not be boosted, got:\n$css")
    }

    @Test
    fun `text and link are inline-block so box modifiers apply`() {
        val txt = SceneText(id = "c_t", text = "hi", modifier = SceneModifier(width = 100, padding = 8, backgroundColor = "#FF0000"))
        val css = CssEmitter.fileCss(txt.id, listOf(txt), emptyMap())

        assertTrue("display: inline-block" in css, "text must be inline-block, got:\n$css")
        assertTrue("width: 100px" in css && "padding: 8px" in css, "box modifiers not emitted, got:\n$css")
    }

    @Test
    fun `all modifier fields emit when set`() {
        val col = SceneColumn(
            id = "c_x",
            arrangement = SceneArrangement.CENTER,
            modifier = SceneModifier(
                fillMaxWidth = true, fillMaxHeight = true, width = 10, height = 20, padding = 4, gap = 6,
                backgroundColor = "#111111", textColor = "#222222", fontSize = 18,
                fontWeight = SceneFontWeight.BOLD,
                textAlign = SceneTextAlign.CENTER,
                cornerRadius = 7, borderWidth = 3, borderColor = "#333333", opacity = 50
            )
        )
        val css = CssEmitter.fileCss(col.id, listOf(col), emptyMap())
        listOf(
            "width: 100%", "height: 100%", "width: 10px", "height: 20px", "padding: 4px", "gap: 6px",
            "background-color: #111111", "color: #222222", "font-size: 18px", "font-weight: 700",
            "text-align: center", "border-radius: 7px", "border: 3px solid #333333", "opacity: 0.5"
        ).forEach {
            assertTrue(it in css, "missing declaration `$it`, got:\n$css")
        }
    }

    @Test
    fun `a node referenced by multiple slots renders once per reference`() {
        val reused = SceneText(id = "c_hi", text = "hi")
        val col = SceneColumn(
            id = "c_root",
            slots = listOf(SceneSlot(id = "s1", childId = "c_hi"), SceneSlot(id = "s2", childId = "c_hi"))
        )
        val pool = mapOf(col.id to col, reused.id to reused)
        val jsx = buildSource { JsxEmitter.emit(this, col, pool, emptyMap(), emptyMap()) }
        // Two slots → two spans.
        val occurrences = jsx.split("<span className=\"n_c_hi\">").size - 1
        assertTrue(occurrences == 2, "expected the reused node rendered twice, got:\n$jsx")
    }

    @Test
    fun `per-corner elliptical radius emits the CSS slash form`() {
        val node = SceneBox(
            id = "c_b",
            modifier = SceneModifier(corners = SceneCornerRadius(
                topLeft = SceneCorner(2, 4), topRight = SceneCorner(0, 0),
                bottomRight = SceneCorner(8, 8), bottomLeft = SceneCorner(1, 3)
            ))
        )
        val css = CssEmitter.fileCss(node.id, listOf(node), emptyMap())
        assertTrue("border-radius: 2px 0px 8px 1px / 4px 0px 8px 3px" in css, "expected per-corner elliptical radius, got:\n$css")
    }

    @Test
    fun `legacy uniform cornerRadius still emits a radius`() {
        val node = SceneBox(id = "c_b", modifier = SceneModifier(cornerRadius = 6))
        val css = CssEmitter.fileCss(node.id, listOf(node), emptyMap())
        assertTrue("border-radius: 6px 6px 6px 6px / 6px 6px 6px 6px" in css, "legacy uniform radius should expand to all corners, got:\n$css")
    }

    @Test
    fun `border position inside outside center emit expected CSS`() {
        fun css(pos: SceneBorderPosition) = CssEmitter.fileCss("c_b", listOf(SceneBox(id = "c_b", modifier = SceneModifier(borderWidth = 2, borderColor = "#FF0000", borderPosition = pos))), emptyMap())
        val inside = css(SceneBorderPosition.INSIDE)
        val outside = css(SceneBorderPosition.OUTSIDE)
        val center = css(SceneBorderPosition.CENTER)
        assertTrue("box-sizing: border-box" in inside && "border: 2px solid #FF0000" in inside, "INSIDE → border-box + border, got:\n$inside")
        assertTrue("box-sizing: content-box" in outside && "border: 2px solid #FF0000" in outside, "OUTSIDE → content-box + border, got:\n$outside")
        assertTrue("border:" !in center && "box-shadow: inset 0 0 0 1px #FF0000, 0 0 0 1px #FF0000" in center, "CENTER → box-shadow ring, no border, got:\n$center")
    }

    @Test
    fun `border plus corner radius emits both so the browser rounds the border`() {
        // A bordered element with a corner radius: CSS `border` + `border-radius` on the same rule →
        // the browser draws a rounded border. (Per-corner + position all on one element.)
        val node = SceneBox(
            id = "c_b",
            modifier = SceneModifier(
                borderWidth = 3, borderColor = "#00FF00", borderPosition = SceneBorderPosition.INSIDE,
                corners = SceneCornerRadius(topLeft = SceneCorner(10, 10), topRight = SceneCorner(10, 10), bottomRight = SceneCorner(10, 10), bottomLeft = SceneCorner(10, 10))
            )
        )
        val css = CssEmitter.fileCss(node.id, listOf(node), emptyMap())
        assertTrue("border: 3px solid #00FF00" in css, "expected a border declaration, got:\n$css")
        assertTrue("border-radius: 10px 10px 10px 10px / 10px 10px 10px 10px" in css, "expected a border-radius declaration, got:\n$css")
        assertTrue("box-sizing: border-box" in css, "INSIDE position → box-sizing: border-box, got:\n$css")
    }
}
