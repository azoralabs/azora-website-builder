package dev.azora.website.builder.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.sdk.compiler.scene.domain.*
import dev.azora.website.builder.domain.*
import dev.azora.sdk.plugin.core.PluginContext
import dev.azora.website.builder.data.WebSceneFiles
import dev.azora.sdk.compiler.scene.domain.SceneDocument
import dev.azora.sdk.compiler.scene.domain.SceneComponentTree

/**
 * "Website Preview" tab: a live, in-Studio WYSIWYG render of the project's pages. Pages and the
 * component library are loaded from disk; component instances are expanded inline (by name) exactly
 * as the generator does.
 */
@Composable
fun WebsitePreviewPanel(context: PluginContext) {
    val palette = MaterialTheme.colorScheme
    val fs = context.fileSystem
    val projectPath = context.projectPath

    var pages by remember(projectPath) { mutableStateOf<List<SceneDocument>>(emptyList()) }
    var componentsByName by remember(projectPath) { mutableStateOf<Map<String, SceneDocument>>(emptyMap()) }
    var selected by remember(projectPath) { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var nav by remember(projectPath) { mutableStateOf<List<NavLink>>(emptyList()) }

    // Selection shared with the node editor. When a selection arrives from the editor for an element
    // that lives on a different page, switch to that page so the highlight is visible. (Instance
    // anchors are real nodes in the page tree, so this resolves them too.)
    val busSelection by WebSelectionBus.selectedId.collectAsState()

    // Live (in-memory) docs published by any open scene editor, overlaid on the disk-loaded sets
    // below so edits appear in the preview the same frame — ahead of the 2 s poll + autosave. The
    // disk poll below remains as a backstop for new files / scenes changed elsewhere.
    val live by WebSceneBus.liveScenes.collectAsState()
    val viewPages = remember(pages, live) {
        val overridden = pages.map { disk ->
            live[disk.name]?.takeIf { it.type == WebSceneType.PAGE }
                ?.let { disk.copy(nodes = it.nodes, rootId = it.rootId, instances = it.instances, route = it.route, nav = it.nav) } ?: disk
        }
        val present = pages.mapTo(mutableSetOf()) { it.name }
        // Append brand-new (unsaved) pages so they appear before their first save+poll too.
        overridden + live.values.filter { it.type == WebSceneType.PAGE && it.name !in present }
    }
    val viewComponents = remember(componentsByName, live) {
        val merged = componentsByName.mapValues { (_, disk) ->
            live[disk.name]?.takeIf { it.type == WebSceneType.COMPONENT }
                ?.let { disk.copy(nodes = it.nodes, rootId = it.rootId, instances = it.instances) } ?: disk
        }.toMutableMap()
        live.values.filter { it.type == WebSceneType.COMPONENT && it.name !in merged }.forEach { merged[it.name] = it }
        merged
    }

    LaunchedEffect(busSelection, viewPages) {
        val id = busSelection ?: return@LaunchedEffect
        if (viewPages.getOrNull(selected)?.let { SceneComponentTree.byId(it.nodes, id) } != null) return@LaunchedEffect
        val idx = viewPages.indexOfFirst { SceneComponentTree.byId(it.nodes, id) != null }
        if (idx >= 0) selected = idx
    }

    // Reload from disk on open, on manual Refresh, and on a poll — so edits made elsewhere (a newly
    // created component, a saved instance) show up without reopening the panel. State is only pushed
    // when something actually changed, to avoid idle recomposition.
    LaunchedEffect(projectPath, refresh) {
        while (true) {
            val loadedPages = WebSceneFiles.loadPages(fs, projectPath)
            val loadedComps = WebSceneFiles.loadComponents(fs, projectPath).associateBy { it.name }
            val pageChanged = loadedPages.size != pages.size ||
                loadedPages.zip(pages).any { (a, b) -> a.name != b.name || a.route != b.route || a.nodes != b.nodes || a.rootId != b.rootId }
            if (pageChanged) pages = loadedPages
            if (loadedComps != componentsByName) componentsByName = loadedComps
            val loadedNav = WebSceneFiles.loadNavigation(fs, projectPath).flatMap { it.nav }
            if (loadedNav != nav) nav = loadedNav
            delay(2000)
        }
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 10.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            viewPages.forEachIndexed { i, p ->
                FilterChip(selected = i == selected, onClick = { selected = i }, label = { Text(p.name.ifBlank { p.route }) })
            }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        HorizontalDivider()
        // Render the page on the site's own background, not the Studio theme: the generated
        // index.css sets `color-scheme: light dark` with no body background, so in light mode the
        // site renders white with near-black default text. Surface also provides that as the default
        // content color, so unstyled text/links inherit it (as they do in the browser).
        Surface(Modifier.fillMaxSize(), color = SiteBackground, contentColor = SiteContentColor) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontSize = 16.sp, color = SiteContentColor)) {
                Column(Modifier.fillMaxSize()) {
                    // Site nav bar (from the navigation .azn) — matches the generated <nav> in App.jsx.
                    if (nav.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            nav.forEach { item ->
                                val idx = viewPages.indexOfFirst { it.route == item.route }
                                val isCurrent = idx >= 0 && idx == selected
                                Text(
                                    item.label,
                                    color = if (isCurrent) SiteContentColor else SiteContentColor.copy(alpha = 0.65f),
                                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable(enabled = idx >= 0) { if (idx >= 0) selected = idx }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = SiteContentColor.copy(alpha = 0.1f))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        val page = viewPages.getOrNull(selected)
                        if (page == null) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No pages yet — create a .azn page.", color = SiteContentColor.copy(alpha = 0.5f))
                            }
                        } else {
                            val pool = page.nodes.associateBy { it.id }
                            val root = pool[page.rootId]
                            if (root != null) SceneComponentView(root, pool, page.instances, viewComponents, busSelection)
                        }
                    }
                }
            }
        }
    }
}

/** The generated site's body background (white) and default text color (black) in light mode —
 *  the emitted index.css sets no body color, so text inherits the browser default. */
private val SiteBackground = Color.White
private val SiteContentColor = Color(0xFF000000)

/**
 * Renders one [SceneComponent] against the scene's node [pool]; container slots resolve to their
 * referenced nodes, so a node reused in several slots renders once per reference. Instance anchors
 * expand the referenced component (switching to that component's pool). [selectedId] is the component
 * id selected in the node editor (via [WebSelectionBus]); the matching element is highlighted, and
 * clicking any element publishes its id back so the editor selects it.
 *
 * [lockedId] is set once we descend into a component instance: the page editor only knows the
 * instance's anchor node, not the component's inner ids, so every click inside the expansion is
 * attributed to that anchor (the inner elements forward to it rather than selecting themselves).
 * [nodePath] guards against slot cycles (a slot referencing an ancestor); [visiting] guards against
 * instance-of-itself cycles by component name.
 */
@Composable
private fun SceneComponentView(
    component: SceneComponent,
    pool: Map<String, SceneComponent>,
    instances: Map<String, String>,
    componentsByName: Map<String, SceneDocument>,
    selectedId: String?,
    lockedId: String? = null,
    visiting: Set<String> = emptySet(),
    nodePath: Set<String> = emptySet()
) {
    if (component.id in nodePath) return // a slot referenced an ancestor
    val childPath = nodePath + component.id

    instances[component.id]?.let { name ->
        val comp = componentsByName[name]
        // Attribute the whole expansion to the outermost anchor (nested instances keep the first).
        val lock = lockedId ?: component.id
        Box(Modifier.selectable(component.id, selectedId, lockedId)) {
            if (comp != null && name !in visiting) {
                val compPool = comp.nodes.associateBy { it.id }
                val compRoot = compPool[comp.rootId]
                if (compRoot != null) {
                    SceneComponentView(compRoot, compPool, comp.instances, componentsByName, selectedId, lock, visiting + name, childPath)
                }
            } else {
                Text("⟨$name⟩", color = SiteContentColor.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        return
    }

    // CSS inheritance: font-size/weight/color/text-align cascade. Merge the ones this node *sets*
    // into the ambient text style (the CssEmitter likewise emits each only when set), so children
    // inherit them — matching the browser.
    CompositionLocalProvider(LocalTextStyle provides component.modifier.mergedTextStyle()) {
        when (component) {
            is SceneColumn -> Column(
                verticalArrangement = columnArrangement(component.arrangement, component.modifier.gap),
                horizontalAlignment = if (component.arrangement == SceneArrangement.CENTER) Alignment.CenterHorizontally else Alignment.Start,
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            ) {
                component.slots.forEach { slot -> slot.childId?.let { pool[it] }?.let { SceneComponentView(it, pool, instances, componentsByName, selectedId, lockedId, visiting, childPath) } }
            }

            is SceneRow -> Row(
                horizontalArrangement = rowArrangement(component.arrangement, component.modifier.gap),
                // CSS align-items: flex-start (center only when arrangement is CENTER).
                verticalAlignment = if (component.arrangement == SceneArrangement.CENTER) Alignment.CenterVertically else Alignment.Top,
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            ) {
                component.slots.forEach { slot -> slot.childId?.let { pool[it] }?.let { SceneComponentView(it, pool, instances, componentsByName, selectedId, lockedId, visiting, childPath) } }
            }

            // CSS: display:flex; flex-direction:column (no justify/align) → a top-aligned flex column.
            is SceneBox -> Column(
                verticalArrangement = columnArrangement(SceneArrangement.START, component.modifier.gap),
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            ) {
                component.slots.forEach { slot -> slot.childId?.let { pool[it] }?.let { SceneComponentView(it, pool, instances, componentsByName, selectedId, lockedId, visiting, childPath) } }
            }

            // <span>: inherits the ambient (cascaded) text style; this node's own font props are
            // already merged into it above.
            is SceneText -> Text(
                text = component.text,
                style = LocalTextStyle.current,
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            )

            // Our custom button — .az-button / AzoraButton primary. Mirrors the generated `.az-button`
            // base (brand purple, white label, 36px tall, 8px radius, Medium 11px label) as the DEFAULT
            // for unset fields, while every modifier the user sets is honored exactly as in the browser.
            is SceneButton -> {
                val mod = component.modifier
                val shape = webCornerShape(mod.effectiveCorners(8))
                // Default height 36 only when neither fillMaxHeight nor an explicit height is set, so
                // those modifiers aren't clobbered by the brand default (matches `.az-button`).
                var base = mod.sizing()
                if (!mod.fillMaxHeight && mod.height == null) base = base.height(36.dp)
                mod.opacity?.let { base = base.alpha(it.coerceIn(0, 100) / 100f) }
                base = base.shadow(4.dp, shape).clip(shape)
                    .background(parseHex(mod.backgroundColor) ?: AzButtonColor)
                mod.borderWidth?.let { w -> base = base.webBorder(w, parseHex(mod.borderColor) ?: Color.Black, mod.borderPosition, mod.effectiveCorners(8)) }
                base = base.selectable(component.id, selectedId, lockedId)
                // padding modifier is all-sides (CssEmitter emits `padding: Npx`); default `.az-button`
                // is horizontal-only (0 24px), so only fall back to that when padding is unset.
                val pad = mod.padding
                base = if (pad != null) base.padding(pad.dp) else base.padding(horizontal = 24.dp, vertical = 0.dp)
                Box(base, contentAlignment = Alignment.Center) {
                    Text(
                        component.label,
                        color = parseHex(mod.textColor) ?: Color.White,
                        fontSize = (mod.fontSize ?: 11).sp,
                        // Default Medium (`.az-button` font-weight 500) when unset; honor the modifier otherwise.
                        fontWeight = if (mod.fontWeight == SceneFontWeight.NORMAL) FontWeight.Medium else mod.fontWeight.toCompose(),
                        maxLines = 1
                    )
                }
            }

            // <a>: underlined; browser default unvisited link color #0000EE unless overridden. Font
            // size/weight inherit from the ambient style.
            is SceneLink -> Text(
                text = component.text,
                style = LocalTextStyle.current.copy(
                    color = parseHex(component.modifier.textColor) ?: Color(0xFF0000EE),
                    textDecoration = TextDecoration.Underline
                ),
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            )

            // <img>: src usually unset in-editor, so show the alt text as a broken image would.
            is SceneImage -> Text(
                component.alt.ifBlank { "image" },
                style = LocalTextStyle.current,
                modifier = component.modifier.toModifier(component.id, selectedId, lockedId)
            )

            // <input>: plain native text field, honoring background/border/clip/alpha/size/padding.
            is SceneInput -> {
                val mod = component.modifier
                val shape = webCornerShape(mod.effectiveCorners(2))
                var base = mod.sizing()
                mod.opacity?.let { base = base.alpha(it.coerceIn(0, 100) / 100f) }
                base = base.clip(shape).background(parseHex(mod.backgroundColor) ?: Color.White)
                    .webBorder(mod.borderWidth ?: 1, parseHex(mod.borderColor) ?: Color(0xFF767676), mod.borderPosition, mod.effectiveCorners(2))
                    .selectable(component.id, selectedId, lockedId)
                    .padding(horizontal = (mod.padding ?: 4).dp, vertical = (mod.padding ?: 2).dp)
                Box(base) {
                    Text(
                        component.placeholder,
                        color = parseHex(mod.textColor) ?: Color(0xFF757575),
                        fontSize = (mod.fontSize ?: 16).sp,
                        maxLines = 1
                    )
                }
            }

            // <div> spacer: no intrinsic height (0) unless one is set.
            is SceneSpacer -> Spacer(modifier = component.modifier.toModifier(component.id, selectedId, lockedId).height((component.modifier.height ?: 0).dp))
        }
    }
}

/** Ambient text style with this modifier's *set* font properties merged in (CSS-style inheritance:
 *  each property is applied only when set, otherwise inherited from the parent). */
@Composable
private fun SceneModifier.mergedTextStyle(): TextStyle {
    var s = LocalTextStyle.current
    fontSize?.let { s = s.copy(fontSize = it.sp) }
    if (fontWeight != SceneFontWeight.NORMAL) s = s.copy(fontWeight = fontWeight.toCompose())
    parseHex(textColor)?.let { s = s.copy(color = it) }
    if (textAlign != SceneTextAlign.START) s = s.copy(
        textAlign = when (textAlign) {
            SceneTextAlign.CENTER -> TextAlign.Center
            SceneTextAlign.END -> TextAlign.End
            SceneTextAlign.START -> TextAlign.Start
        }
    )
    return s
}

// CSS containers carry both justify-content (the arrangement) and `gap`; combine them with spacedBy.
private fun columnArrangement(a: SceneArrangement, gap: Int?): Arrangement.Vertical = when (a) {
    SceneArrangement.START -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.Top) } ?: Arrangement.Top
    SceneArrangement.CENTER -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.CenterVertically) } ?: Arrangement.Center
    SceneArrangement.END -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.Bottom) } ?: Arrangement.Bottom
    SceneArrangement.SPACE_BETWEEN -> Arrangement.SpaceBetween
}

private fun rowArrangement(a: SceneArrangement, gap: Int?): Arrangement.Horizontal = when (a) {
    SceneArrangement.START -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.Start) } ?: Arrangement.Start
    SceneArrangement.CENTER -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.CenterHorizontally) } ?: Arrangement.Center
    SceneArrangement.END -> gap?.let { Arrangement.spacedBy(it.dp, Alignment.End) } ?: Arrangement.End
    SceneArrangement.SPACE_BETWEEN -> Arrangement.SpaceBetween
}

/** Size-only modifier (width/height/fill) — the part shared by elements that draw their own chrome. */
private fun SceneModifier.sizing(): Modifier {
    var m: Modifier = Modifier
    if (fillMaxWidth) m = m.fillMaxWidth()
    if (fillMaxHeight) m = m.fillMaxHeight()
    width?.let { m = m.width(it.dp) }
    height?.let { m = m.height(it.dp) }
    return m
}

/** Resolved per-corner (possibly elliptical) radius: [corners] if set, else legacy uniform
 *  [cornerRadius], else [default] (a brand default for chrome-drawing elements like buttons). */
private fun SceneModifier.effectiveCorners(default: Int): SceneCornerRadius =
    corners ?: cornerRadius?.let { SceneCornerRadius.uniform(it) } ?: SceneCornerRadius.uniform(default)

/** A Compose [Shape] with per-corner, optionally-elliptical radii (matches the generated CSS slash form). */
private fun webCornerShape(c: SceneCornerRadius): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun corner(corner: SceneCorner) = with(density) { CornerRadius(corner.x.dp.toPx(), corner.y.dp.toPx()) }
        return Outline.Rounded(
            RoundRect(
                left = 0f, top = 0f, right = size.width, bottom = size.height,
                topLeftCornerRadius = corner(c.topLeft),
                topRightCornerRadius = corner(c.topRight),
                bottomRightCornerRadius = corner(c.bottomRight),
                bottomLeftCornerRadius = corner(c.bottomLeft)
            )
        )
    }
}

/** Draws the border at [position] relative to the box edge (inside / outside / center), following the
 *  per-corner radii — matching the generated CSS. Implemented as a filled rounded-rect **ring** (outer
 *  minus inner via even-odd fill), which avoids stroke-centering/clipping quirks and always renders
 *  rounded corners. Applied before the corner clip in [toModifier] so an outside/center border can
 *  extend past the edge. */
private fun Modifier.webBorder(width: Int, color: Color, position: SceneBorderPosition, corners: SceneCornerRadius): Modifier =
    this.drawWithContent {
        drawContent()
        if (width <= 0) return@drawWithContent
        val w = width.dp.toPx()
        // (outerInset, innerInset): the border band is [outerInset, innerInset] from the bounds edge.
        val outerInset: Float
        val innerInset: Float
        when (position) {
            SceneBorderPosition.INSIDE -> { outerInset = 0f; innerInset = w }
            SceneBorderPosition.OUTSIDE -> { outerInset = -w; innerInset = 0f }
            SceneBorderPosition.CENTER -> { outerInset = -w / 2f; innerInset = w / 2f }
        }
        fun ringRect(inset: Float): RoundRect {
            // Corner radius tracks the inset: radius = declared radius − inset (outset grows it).
            fun c(corner: SceneCorner) = CornerRadius(
                (corner.x.dp.toPx() - inset).coerceAtLeast(0f),
                (corner.y.dp.toPx() - inset).coerceAtLeast(0f)
            )
            return RoundRect(
                left = inset, top = inset, right = size.width - inset, bottom = size.height - inset,
                topLeftCornerRadius = c(corners.topLeft), topRightCornerRadius = c(corners.topRight),
                bottomRightCornerRadius = c(corners.bottomRight), bottomLeftCornerRadius = c(corners.bottomLeft)
            )
        }
        val ring = Path().apply {
            addRoundRect(ringRect(outerInset))
            addRoundRect(ringRect(innerInset))
            fillType = PathFillType.EvenOdd // outer fill with an inner hole = the border band
        }
        drawPath(path = ring, color = color)
    }

private fun SceneModifier.toModifier(id: String, selectedId: String?, lockedId: String?): Modifier {
    val corners = effectiveCorners(0)
    val shape = webCornerShape(corners)
    var m = sizing()
    opacity?.let { m = m.alpha((it.coerceIn(0, 100)) / 100f) }
    // Border is applied before background/clip so its draw runs outside the corner clip — an
    // outside/center border can extend past the box edge, matching the CSS (see CssEmitter).
    borderWidth?.let { w -> m = m.webBorder(w, parseHex(borderColor) ?: Color.Black, borderPosition, corners) }
    backgroundColor?.let { hex -> parseHex(hex)?.let { c -> m = m.background(c, shape) } }
    if (!corners.isZero()) m = m.clip(shape)
    padding?.let { m = m.padding(it.dp) }
    return m.selectable(id, selectedId, lockedId)
}

/** Click-to-select, matching the React output's lack of element borders: only the *selected* element
 *  draws an outline. Inside a component instance ([lockedId] set), clicks select the anchor node and
 *  no per-element outline is drawn (the instance block carries the single highlight). */
private fun Modifier.selectable(id: String, selectedId: String?, lockedId: String?): Modifier =
    if (lockedId == null) {
        (if (id == selectedId) this.border(2.dp, SelectionHighlight) else this)
            .clickable { WebSelectionBus.select(id) }
    } else {
        this.clickable { WebSelectionBus.select(lockedId) }
    }

/** Highlight color for the element selected in the node editor. */
private val SelectionHighlight = Color(0xFF2563EB)

/** Our button's brand fill (Azora primary), matching the generated .az-button background. */
private val AzButtonColor = Color(0xFFD14EEA)

private fun SceneFontWeight.toCompose() = when (this) {
    SceneFontWeight.NORMAL -> FontWeight.Normal
    SceneFontWeight.MEDIUM -> FontWeight.Medium
    SceneFontWeight.SEMI_BOLD -> FontWeight.SemiBold
    SceneFontWeight.BOLD -> FontWeight.Bold
}

private fun parseHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            else -> null
        }
    }.getOrNull()
}
