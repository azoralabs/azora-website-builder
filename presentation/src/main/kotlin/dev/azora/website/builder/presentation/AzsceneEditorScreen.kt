package dev.azora.website.builder.presentation
import dev.azora.sdk.plugin.core.AbstractPluginUndoRedo
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.sdk.compiler.scene.domain.SceneColumn
import dev.azora.sdk.plugin.core.PluginContext
import dev.azora.canvas.presentation.menu.*
import dev.azora.website.builder.presentation.node.*
import dev.azora.sdk.compiler.scene.domain.*
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

    var doc by remember(filePath) { mutableStateOf<SceneDocument?>(null) }
    var components by remember(filePath) { mutableStateOf<List<SceneDocument>>(emptyList()) }
    var pages by remember(filePath) { mutableStateOf<List<SceneDocument>>(emptyList()) }
    var dirty by remember(filePath) { mutableStateOf(false) }

    // Undo/redo — registered with the host coordinator so toolbar buttons work (same pattern as
    // AzoraNodesViewModel).
    val undoRedo = remember(filePath) { WebSceneUndoRedoProvider(filePath) }
    val undoFacade = context.undoRedo
    DisposableEffect(filePath) {
        undoFacade?.register(undoRedo)
        undoFacade?.setActive(undoRedo.providerId)
        onDispose { undoFacade?.unregister(undoRedo.providerId) }
    }

    LaunchedEffect(filePath) {
        val loaded = WebSceneFiles.read(fs, filePath)
            ?: if (type == WebSceneType.PAGE || type == WebSceneType.COMPONENT) SceneDocument.withRoot(type = type, name = baseName)
            else SceneDocument(type = type, name = baseName)
        doc = loaded
        undoRedo.setCurrent(loaded)
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
            if (loaded.map(SceneDocument::name) != components.map(SceneDocument::name)) {
                components = loaded
            }
            if (type == WebSceneType.NAVIGATION) {
                val loadedPages = WebSceneFiles.loadPages(fs, projectPath)
                if (loadedPages.map(SceneDocument::name) != pages.map(SceneDocument::name)) {
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

    // Restore callback — invoked by undo/redo to set the doc.
    undoRedo.onRestoreCb = { restored -> doc = restored; dirty = true }

    fun update(next: SceneDocument) {
        val prev = doc
        if (prev != null) undoRedo.pushState(prev)
        doc = next; dirty = true
        undoRedo.setCurrent(next)
        undoFacade?.setActive(undoRedo.providerId)
    }

    fun mutate(transform: (SceneDocument) -> SceneDocument) {
        val latest = doc ?: return
        undoRedo.pushState(latest)
        val next = transform(latest)
        doc = next; dirty = true
        undoRedo.setCurrent(next)
        undoFacade?.setActive(undoRedo.providerId)
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

    Column(Modifier.fillMaxSize().background(palette.background)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when {
                    event.isCtrlPressed && event.key == Key.Z && !event.isShiftPressed -> { undoRedo.undo(); true }
                    event.isCtrlPressed && (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed)) -> { undoRedo.redo(); true }
                    else -> false
                }
            } else false
        }
    ) {
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
    doc: SceneDocument,
    onChange: (SceneDocument) -> Unit,
    context: PluginContext? = null,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme
    fun setting(key: String) = doc.settings[key] ?: ""
    fun put(key: String, value: String) = onChange(doc.copy(settings = doc.settings + (key to value)))

    // Nav-file picker state (needs PluginContext for the file system + project extras).
    var navCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var navExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(context?.projectPath) {
        context?.let { navCandidates = WebSceneFiles.navigationFilePaths(it.fileSystem, it.projectPath) }
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
                    value = setting("navFile").ifBlank { "— not set —" },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Box {
                            TextButton(onClick = { navExpanded = true }) { Text("Browse…", fontSize = 11.sp) }
                            DropdownMenu(expanded = navExpanded, onDismissRequest = { navExpanded = false }) {
                                navCandidates.forEach { path ->
                                    DropdownMenuItem(
                                        text = { Text(path.substringAfterLast('/'), fontSize = 12.sp) },
                                        onClick = {
                                            put("navFile", path.substringAfterLast('/'))
                                            navExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    textStyle = TextStyle(fontSize = 12.sp)
                )
                TextButton(onClick = {
                    val name = setting("navFile")
                    if (name.isNotBlank() && context != null) {
                        val fullPath = navCandidates.firstOrNull { it.endsWith(name) }
                        if (fullPath != null) context.openScene(fullPath)
                    }
                }, enabled = setting("navFile").isNotBlank()) {
                    Text("Open", fontSize = 11.sp)
                }
            }
        }

        Text("Settings are saved into project.azora.", color = palette.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

/**
 * Full navigation node editor — everything via the canvas:
 * - **Right-click empty canvas → "New route"** creates a route node.
 * - **Right-click a route node → "Duplicate" / "Delete"**.
 * - **Drag from "Site Navigation" to a page** creates a route linked to that page.
 * - **Click a route node → properties panel** (right) shows only that route's editable label + route.
 */
@Composable
private fun ConfigNavEditor(
    doc: SceneDocument,
    pages: List<SceneDocument>,
    onChange: (SceneDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val entryId = "__nav__"
    val palette = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedNavIndex by remember { mutableStateOf<Int?>(null) }
    val localPositions = remember { mutableStateMapOf<String, Offset>() }

    fun nodeId(route: String) = "nav_entry_$route"
    fun pos(id: String): Offset =
        localPositions[id] ?: doc.positions[id]?.let { Offset(it.x, it.y) }
            ?: if (id == entryId) Offset(40f, 200f) else Offset(320f, 60f + doc.nav.indexOfFirst { nodeId(it.route) == id } * 110f)

    fun updateNav(newNav: List<NavLink>) = onChange(doc.copy(nav = newNav))
    fun persistPositions() = onChange(doc.copy(positions = doc.positions + localPositions.mapValues { CanvasPoint(it.value.x, it.value.y) }))

    // Sync canvas selection → nav index
    fun selectNode(nodeId: String?) {
        selected = nodeId
        selectedNavIndex = nodeId?.let { nid -> doc.nav.indexOfFirst { nodeId(it.route) == nid }.takeIf { it >= 0 } }
    }

    val navNode = WebNode(
        id = entryId, position = pos(entryId), title = "Site Navigation", subtitle = "${doc.nav.size} route(s)",
        accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange, hasInput = false,
        outputs = listOf(WebPort("nav", "nav", dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange))
    )
    val routeNodes = doc.nav.map { entry ->
        WebNode(
            id = nodeId(entry.route), position = pos(nodeId(entry.route)),
            title = entry.label.ifBlank { entry.route }, subtitle = entry.route,
            accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentPurple, hasInput = true
        )
    }
    val links = doc.nav.map { entry ->
        WebNodeLink("nav:${entry.route}", entryId, "nav", nodeId(entry.route), dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange.copy(alpha = 0.7f))
    }

    Row(modifier) {
        // --- Canvas ---
        WebNodeCanvas(
            modifier = Modifier.weight(1f),
            nodes = listOf(navNode) + routeNodes, links = links, selectedNodeId = selected,
            onSelect = { selectNode(it) },
            onLink = { _, _, _ -> },
            // Right-click empty canvas → New route
            contextMenu = { screenPos, worldPos, onDismiss ->
                NodeContextMenu(
                    position = screenPos,
                    items = listOf(NodeMenuItem("New route") {
                        val entry = NavLink("New Route", "/new-route-${doc.nav.size + 1}")
                        localPositions[nodeId(entry.route)] = worldPos
                        updateNav(doc.nav + entry)
                        selectNode(nodeId(entry.route))
                        onDismiss()
                    }),
                    onDismiss = onDismiss
                )
            },
            // Right-click a route node → Duplicate / Delete
            nodeContextMenu = { nodeId, screenPos, onDismiss ->
                val idx = doc.nav.indexOfFirst { nodeId(it.route) == nodeId }
                if (idx >= 0) {
                    NodeContextMenu(
                        position = screenPos,
                        items = listOf(
                            NodeMenuItem("Duplicate") {
                                val entry = doc.nav[idx]
                                updateNav(doc.nav.toMutableList().also { it.add(idx + 1, NavLink("${entry.label} (copy)", "${entry.route}_copy")) })
                                onDismiss()
                            },
                            NodeMenuItem("Delete", color = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentRed) {
                                updateNav(doc.nav.filterIndexed { i, _ -> i != idx })
                                selectNode(null)
                                onDismiss()
                            }
                        ),
                        onDismiss = onDismiss
                    )
                } else onDismiss()
            },
            onNodeMove = { id, p -> localPositions[id] = p },
            onNodeMoveEnd = { persistPositions() }
        )

        // --- Properties panel: only the selected route ---
        Column(Modifier.width(286.dp).fillMaxHeight().background(palette.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val idx = selectedNavIndex
            if (idx != null && idx < doc.nav.size) {
                val entry = doc.nav[idx]
                val external = entry.route.startsWith("http")
                Text("Route properties", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(
                    value = entry.label, onValueChange = { newLabel ->
                        updateNav(doc.nav.mapIndexed { j, e -> if (j == idx) NavLink(newLabel, e.route) else e })
                    }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = entry.route, onValueChange = { newRoute ->
                        updateNav(doc.nav.mapIndexed { j, e -> if (j == idx) NavLink(e.label, newRoute) else e })
                    }, label = { Text(if (external) "URL (external)" else "Route") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 13.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val ni = (idx - 1).coerceAtLeast(0)
                        if (ni != idx) { val l = doc.nav.toMutableList(); l.add(ni, l.removeAt(idx)); updateNav(l); selectedNavIndex = ni }
                    }, enabled = idx > 0) { Text("▲") }
                    OutlinedButton(onClick = {
                        val ni = (idx + 1).coerceAtMost(doc.nav.lastIndex)
                        if (ni != idx) { val l = doc.nav.toMutableList(); l.add(ni, l.removeAt(idx)); updateNav(l); selectedNavIndex = ni }
                    }, enabled = idx < doc.nav.lastIndex) { Text("▼") }
                }
                TextButton(onClick = {
                    updateNav(doc.nav.filterIndexed { i, _ -> i != idx }); selectNode(null)
                }) { Text("Delete route", color = palette.error) }
            } else {
                Text("No route selected", color = palette.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Right-click the canvas → New route.\nClick a route node to edit it.",
                    color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp)
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

/** Undo/redo provider for a website scene — extends the SDK's [AbstractPluginUndoRedo] base. */
private class WebSceneUndoRedoProvider(filePath: String) : AbstractPluginUndoRedo<SceneDocument>() {
    override val providerId = "website_$filePath"
    var onRestoreCb: ((SceneDocument) -> Unit)? = null
    override fun onRestore(state: SceneDocument) { onRestoreCb?.invoke(state) }
}
