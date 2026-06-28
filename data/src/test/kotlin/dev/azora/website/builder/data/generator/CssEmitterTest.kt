package dev.azora.website.builder.data.generator

import dev.azora.website.builder.domain.WebArrangement
import dev.azora.website.builder.domain.WebButton
import dev.azora.website.builder.domain.WebColumn
import dev.azora.website.builder.domain.WebFontWeight
import dev.azora.website.builder.domain.WebModifier
import dev.azora.website.builder.domain.WebSlot
import dev.azora.website.builder.domain.WebText
import dev.azora.website.builder.domain.WebTextAlign
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
        val btn = WebButton(
            id = "c_abc",
            label = "Go",
            modifier = WebModifier(height = 50, borderWidth = 2, borderColor = "#000000")
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
        val btn = WebButton(id = "c_abc", label = "Go")
        val jsx = buildSource { JsxEmitter.emit(this, btn, mapOf(btn.id to btn), emptyMap(), emptyMap()) }
        // AzButton merges this className with `az-button`, so `.az-button.n_c_abc` matches the element.
        assertTrue("<AzButton className=\"n_c_abc\">" in jsx, "button JSX must carry its class, got:\n$jsx")
    }

    @Test
    fun `non-button nodes use the plain per-node selector`() {
        val text = WebText(id = "c_t", text = "hi", modifier = WebModifier(width = 100))
        val col = WebColumn(
            id = "c_root",
            modifier = WebModifier(fillMaxWidth = true, padding = 16),
            slots = listOf(WebSlot(id = "s1", childId = "c_t"))
        )
        val css = CssEmitter.fileCss(col.id, listOf(col, text), emptyMap())

        assertTrue(".n_c_root {" in css, "container selector, got:\n$css")
        assertTrue(".n_c_t {" in css, "text selector, got:\n$css")
        assertFalse(".az-button.n_c_root" in css, "non-button must not be boosted, got:\n$css")
    }

    @Test
    fun `text and link are inline-block so box modifiers apply`() {
        val txt = WebText(id = "c_t", text = "hi", modifier = WebModifier(width = 100, padding = 8, backgroundColor = "#FF0000"))
        val css = CssEmitter.fileCss(txt.id, listOf(txt), emptyMap())

        assertTrue("display: inline-block" in css, "text must be inline-block, got:\n$css")
        assertTrue("width: 100px" in css && "padding: 8px" in css, "box modifiers not emitted, got:\n$css")
    }

    @Test
    fun `all modifier fields emit when set`() {
        val col = WebColumn(
            id = "c_x",
            arrangement = WebArrangement.CENTER,
            modifier = WebModifier(
                fillMaxWidth = true, fillMaxHeight = true, width = 10, height = 20, padding = 4, gap = 6,
                backgroundColor = "#111111", textColor = "#222222", fontSize = 18,
                fontWeight = WebFontWeight.BOLD,
                textAlign = WebTextAlign.CENTER,
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
        val reused = WebText(id = "c_hi", text = "hi")
        val col = WebColumn(
            id = "c_root",
            slots = listOf(WebSlot(id = "s1", childId = "c_hi"), WebSlot(id = "s2", childId = "c_hi"))
        )
        val pool = mapOf(col.id to col, reused.id to reused)
        val jsx = buildSource { JsxEmitter.emit(this, col, pool, emptyMap(), emptyMap()) }
        // Two slots → two spans.
        val occurrences = jsx.split("<span className=\"n_c_hi\">").size - 1
        assertTrue(occurrences == 2, "expected the reused node rendered twice, got:\n$jsx")
    }
}
