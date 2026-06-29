package dev.azora.website.builder.presentation
import dev.azora.website.builder.data.WebSceneFiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.website.builder.domain.WebColumn
import dev.azora.sdk.plugin.core.PluginContext
import dev.azora.website.builder.presentation.node.*
import dev.azora.website.builder.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The editor Studio shows for a website `.azscene` file, chosen by its [type]:
 * - [WebSceneType.PAGE] / [WebSceneType.COMPONENT] → the visual node editor ([WebSceneEditor]).
 * - [WebSceneType.CONFIG] → the navigation editor; its nav is also written into `project.azora`.
 *
 * Loads/saves the file via [context]'s file system; **Generate** emits the React app (in-plugin).
 */
@Composable
fun AzsceneEditorScreen(type: String, filePath: String, context: PluginContext) {
    val palette = MaterialTheme.colorScheme
    val fs = context.fileSystem
    val projectPath = context.projectPath
    val scope = rememberCoroutineScope()
    val baseName = filePath.substringAfterLast('/').removeSuffix(WebSceneFiles.EXT)

    var doc by remember(filePath) { mutableStateOf<WebSceneDoc?>(null) }
    var components by remember(filePath) { mutableStateOf<List<WebSceneDoc>>(emptyList()) }
    var pages by remember(filePath) { mutableStateOf<List<WebSceneDoc>>(emptyList()) }
    var dirty by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        doc = WebSceneFiles.read(fs, filePath)
            ?: if (type == WebSceneType.PAGE || type == WebSceneType.COMPONENT) WebSceneDoc.withRoot(type = type, name = baseName)
            else WebSceneDoc(type = type, name = baseName)
        components = WebSceneFiles.loadComponents(fs, projectPath)
        if (type == WebSceneType.NAVIGATION) {
            pages = WebSceneFiles.loadPages(fs, projectPath)
        }
        // Keep the component library fresh while this scene is open, so a component created in
        // another file (e.g. just now in the file browser) shows up in the instance dropdown
        // without having to close and reopen this scene. Only write state when the name set
        // actually changes, to avoid needless recomposition.
        while (true) {
            delay(1200)
            val loaded = WebSceneFiles.loadComponents(fs, projectPath)
            if (loaded.map(WebSceneDoc::name) != components.map(WebSceneDoc::name)) {
                components = loaded
            }
            if (type == WebSceneType.NAVIGATION) {
                val loadedPages = WebSceneFiles.loadPages(fs, projectPath)
                if (loadedPages.map(WebSceneDoc::name) != pages.map(WebSceneDoc::name)) {
                    pages = loadedPages
                }
            }
        }
    }

    val current = doc
    if (current == null) {
        Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
            Text("Loading…", color = palette.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    fun update(next: WebSceneDoc) { doc = next; dirty = true }

    /** Applies [transform] to the *latest* doc. Unlike [update], several edits fired in a single
     *  event compose instead of each branching from a stale snapshot and clobbering the others —
     *  e.g. inserting a component instance records its name ([onInstance]), its canvas position
     *  ([onPersistPosition]) and adds its anchor node ([onRootChange]) in one go. */
    fun mutate(transform: (WebSceneDoc) -> WebSceneDoc) {
        val latest = doc ?: return
        doc = transform(latest)
        dirty = true
    }

    // Autosave: every edit marks the doc dirty; this debounces writes so a burst of edits collapses
    // into one save ~600ms after you stop. No Save button, no toolbar — saving is silent.
    // Re-keying on `current` cancels the pending save and restarts the timer on each new edit.
    LaunchedEffect(current, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(600)
        WebSceneFiles.write(fs, filePath, current.copy(name = baseName, type = type))
        when (type) {
            WebSceneType.NAVIGATION -> context.saveProject(WebsiteConfig.withNav(context.project, current.nav))
            WebSceneType.CONFIG -> context.saveProject(WebsiteConfig.withSettings(context.project, current.settings))
            else -> {}
        }
        dirty = false
    }

    // Push the live (in-memory) doc to the Website Preview so edits show instantly — ahead of the
    // debounced disk write above and the preview's 2 s poll. Retract on close so stale live data
    // can't shadow disk once this editor leaves. (Only PAGE/COMPONENT docs have a renderable root;
    // publishing the others is harmless — the preview ignores live docs that aren't pages/components.)
    LaunchedEffect(current) { WebSceneBus.publish(current.copy(name = baseName)) }
    DisposableEffect(filePath) { onDispose { WebSceneBus.retract(baseName) } }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        when (type) {
            WebSceneType.NAVIGATION -> ConfigNavEditor(current, pages, onChange = ::update, modifier = Modifier.weight(1f))
            WebSceneType.CONFIG -> ConfigForm(current, onChange = ::update, context = context, modifier = Modifier.weight(1f))
            else -> WebSceneEditor(
                nodes = current.nodes,
                rootId = current.rootId,
                positions = current.positions,
                onNodesChange = { n -> mutate { d -> d.copy(nodes = n) } },
                onPersistPosition = { id, pt -> mutate { d -> d.copy(positions = d.positions + (id to pt)) } },
                modifier = Modifier.weight(1f),
                components = components,
                excludeName = if (type == WebSceneType.COMPONENT) baseName else null,
                instances = current.instances,
                onInstance = { nodeId, name -> mutate { d -> d.copy(instances = d.instances + (nodeId to name)) } },
                onOpenComponent = { name ->
                    scope.launch {
                        // Components can live anywhere in the project, so resolve the real file by name
                        // rather than assuming the conventional components/ folder.
                        val path = WebSceneFiles.findComponentPath(fs, projectPath, name)
                            ?: WebSceneFiles.componentPath(projectPath, name)
                        context.openScene(path)
                    }
                }
            )
        }
    }
}

/** Site config form: settings (title/description/themeColor) + a navigation .azn file picker.
 *  Settings are mirrored into `project.azora` on save; the nav path is stored in project extras. */
@Composable
private fun ConfigForm(
    doc: WebSceneDoc,
    onChange: (WebSceneDoc) -> Unit,
    context: PluginContext? = null,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme
    fun setting(key: String) = doc.settings[key] ?: ""
    fun put(key: String, value: String) = onChange(doc.copy(settings = doc.settings + (key to value)))

    // Nav-file picker state (needs PluginContext for the file system + project extras).
    var allAzn by remember { mutableStateOf<List<String>>(emptyList()) }
    var navPath by remember { mutableStateOf(context?.let { WebsiteConfig.navPath(it.project) } ?: "") }
    var navExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(context?.projectPath) {
        context?.let { allAzn = WebSceneFiles.listAllAzscenePaths(it.fileSystem, it.projectPath) }
    }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Site configuration", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        OutlinedTextField(setting("title"), { put("title", it) }, label = { Text("Site title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(setting("description"), { put("description", it) }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(setting("themeColor"), { put("themeColor", it) }, label = { Text("Theme color (#RRGGBB)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        if (context != null) {
            HorizontalDivider(color = palette.outlineVariant)
            Text("Navigation file", color = palette.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = navPath.substringAfterLast('/').ifBlank { "— not set —" },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Box {
                            TextButton(onClick = { navExpanded = true }) { Text("Browse…", fontSize = 11.sp) }
                            DropdownMenu(expanded = navExpanded, onDismissRequest = { navExpanded = false }) {
                                allAzn.forEach { path ->
                                    DropdownMenuItem(
                                        text = { Text(path.substringAfterLast('/'), fontSize = 12.sp) },
                                        onClick = {
                                            navPath = path
                                            context.saveProject(WebsiteConfig.withNavPath(context.project, path))
                                            navExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    textStyle = TextStyle(fontSize = 12.sp)
                )
                TextButton(onClick = { if (navPath.isNotBlank()) context.openScene(navPath) }, enabled = navPath.isNotBlank()) {
                    Text("Open", fontSize = 11.sp)
                }
            }
        }

        Text("Settings are saved into project.azora.", color = palette.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

/**
 * Full navigation node editor: a two-pane layout.
 * - **Canvas (left):** visual graph — drag from "Site Navigation" to a page node to add a nav entry;
 *   right-click a page node → "Remove from nav" (if linked).
 * - **Inspector (right):** ordered nav list with editable label/route, reorder (▲▼), remove (×), and
 *   an "Add custom route" button for entries that don't map to a page (e.g. /blog, external links).
 */
@Composable
private fun ConfigNavEditor(
    doc: WebSceneDoc,
    pages: List<WebSceneDoc>,
    onChange: (WebSceneDoc) -> Unit,
    modifier: Modifier = Modifier
) {
    val entryId = "__nav__"
    var selected by remember { mutableStateOf<String?>(null) }
    val localPositions = remember { mutableStateMapOf<String, Offset>() }
    val palette = MaterialTheme.colorScheme

    fun pos(id: String): Offset =
        localPositions[id] ?: doc.positions[id]?.let { Offset(it.x, it.y) }
            ?: if (id == entryId) Offset(40f, 160f) else Offset(360f, 40f + pages.indexOfFirst { it.route == id } * 96f)

    fun updateNav(newNav: List<NavLink>) = onChange(doc.copy(nav = newNav))

    val navNode = WebNode(
        id = entryId, position = pos(entryId), title = "Site Navigation", subtitle = "${doc.nav.size} item(s)",
        accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange, hasInput = false,
        outputs = listOf(WebPort("nav", "nav", dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange))
    )
    val pageNodes = pages.map { p ->
        WebNode(id = p.route, position = pos(p.route), title = p.name.ifBlank { p.route }, subtitle = p.route,
            accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentBlue, hasInput = true)
    }
    val links = doc.nav.mapNotNull { item ->
        pages.firstOrNull { it.route == item.route }
            ?.let { WebNodeLink("nav:${item.route}", entryId, "nav", it.route, dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange) }
    }

    Row(modifier) {
        WebNodeCanvas(
            modifier = Modifier.weight(1f),
            nodes = listOf(navNode) + pageNodes, links = links, selectedNodeId = selected,
            onSelect = { selected = it },
            onLink = { sourceId, _, targetRoute ->
                if (sourceId == entryId && doc.nav.none { it.route == targetRoute })
                    pages.firstOrNull { it.route == targetRoute }?.let { p ->
                        updateNav(doc.nav + NavLink(p.name.ifBlank { p.route }, p.route))
                    }
            },
            nodeContextMenu = { nodeId, screenPos, onDismiss ->
                if (nodeId != entryId && doc.nav.any { it.route == nodeId }) {
                    NodeContextMenu(position = screenPos,
                        items = listOf(NodeMenuItem("Remove from nav", color = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentRed) {
                            updateNav(doc.nav.filterNot { it.route == nodeId }); onDismiss()
                        }), onDismiss = onDismiss)
                } else onDismiss()
            },
            onNodeMove = { id, p -> localPositions[id] = p },
            onNodeMoveEnd = {
                onChange(doc.copy(positions = doc.positions + localPositions.mapValues { CanvasPoint(it.value.x, it.value.y) }))
            }
        )
        // --- inspector: ordered nav list with full CRUD ---
        Column(Modifier.width(300.dp).fillMaxHeight().background(palette.surface).padding(12.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Navigation entries", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (doc.nav.isEmpty()) Text("No entries. Drag from the nav node to a page, or add a custom route below.",
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            doc.nav.forEachIndexed { i, entry ->
                NavEntryRow(entry, i, doc.nav.lastIndex,
                    onEdit = { label, route -> updateNav(doc.nav.mapIndexed { j, e -> if (j == i) NavLink(label, route) else e }) },
                    onMove = { delta ->
                        val ni = (i + delta).coerceIn(0, doc.nav.lastIndex)
                        if (ni != i) { val l = doc.nav.toMutableList(); l.add(ni, l.removeAt(i)); updateNav(l) }
                    },
                    onRemove = { updateNav(doc.nav.filterNot { it.route == entry.route }) }
                )
            }
            OutlinedButton(onClick = { updateNav(doc.nav + NavLink("New Link", "/new")) }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add custom route", fontSize = 11.sp)
            }
        }
    }
}

/** One nav entry in the inspector: editable label + route, reorder, remove. */
@Composable
private fun NavEntryRow(entry: NavLink, index: Int, lastIndex: Int, onEdit: (String, String) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val external = entry.route.startsWith("http")
    var label by remember(entry.label) { mutableStateOf(entry.label) }
    var route by remember(entry.route) { mutableStateOf(entry.route) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
        .background(palette.surfaceVariant).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("#${index + 1}", color = palette.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("▲", color = if (index == 0) palette.onSurface.copy(alpha = 0.3f) else palette.onSurface, fontSize = 14.sp, modifier = Modifier.clickable(enabled = index > 0) { onMove(-1) }.padding(2.dp))
                Text("▼", color = if (index == lastIndex) palette.onSurface.copy(alpha = 0.3f) else palette.onSurface, fontSize = 14.sp, modifier = Modifier.clickable(enabled = index < lastIndex) { onMove(1) }.padding(2.dp))
                Text("×", color = palette.error, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onRemove() }.padding(horizontal = 4.dp))
            }
        }
        OutlinedTextField(value = label, onValueChange = { label = it; onEdit(it, route) }, label = { Text("Label", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp))
        OutlinedTextField(value = route, onValueChange = { route = it; onEdit(label, it) }, label = { Text(if (external) "URL (external)" else "Route", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp))
    }
}
