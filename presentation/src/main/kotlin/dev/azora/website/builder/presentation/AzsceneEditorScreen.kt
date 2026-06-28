package dev.azora.website.builder.presentation
import dev.azora.website.builder.data.WebSceneFiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
            WebSceneType.CONFIG -> ConfigForm(current, onChange = ::update, modifier = Modifier.weight(1f))
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

/** Site config form: simple key/value settings, mirrored into `project.azora` on save. */
@Composable
private fun ConfigForm(doc: WebSceneDoc, onChange: (WebSceneDoc) -> Unit, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    fun setting(key: String) = doc.settings[key] ?: ""
    fun put(key: String, value: String) = onChange(doc.copy(settings = doc.settings + (key to value)))

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Site configuration", color = palette.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        OutlinedTextField(setting("title"), { put("title", it) }, label = { Text("Site title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(setting("description"), { put("description", it) }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(setting("themeColor"), { put("themeColor", it) }, label = { Text("Theme color (#RRGGBB)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Saved into project.azora.", color = palette.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

/** Site-navigation editor: drag from the Navigation node to a page to add it to the nav bar. */
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

    fun pos(id: String): Offset =
        localPositions[id] ?: doc.positions[id]?.let { Offset(it.x, it.y) }
            ?: if (id == entryId) Offset(40f, 160f) else Offset(360f, 40f + pages.indexOfFirst { it.route == id } * 96f)

    val navNode = WebNode(
        id = entryId, position = pos(entryId), title = "Site Navigation", subtitle = "${doc.nav.size} item(s)",
        accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange, hasInput = false,
        outputs = listOf(WebPort("nav", "nav", dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange))
    )
    val pageNodes = pages.map { p ->
        WebNode(
            id = p.route, position = pos(p.route), title = p.name.ifBlank { p.route }, subtitle = p.route,
            accent = dev.azora.sdk.core.theme.palette.AzoraPalette.AccentBlue, hasInput = true, outputs = emptyList()
        )
    }
    val links = doc.nav.mapNotNull { item ->
        val p = pages.firstOrNull { it.route == item.route } ?: return@mapNotNull null
        WebNodeLink("nav:${item.route}", entryId, "nav", p.route, dev.azora.sdk.core.theme.palette.AzoraPalette.AccentOrange)
    }

    WebNodeCanvas(
        modifier = modifier,
        nodes = listOf(navNode) + pageNodes,
        links = links,
        selectedNodeId = selected,
        onSelect = { selected = it },
        onLink = { sourceId, _, targetRoute ->
            if (sourceId == entryId && doc.nav.none { it.route == targetRoute }) {
                val page = pages.firstOrNull { it.route == targetRoute }
                if (page != null) onChange(doc.copy(nav = doc.nav + NavLink(page.name.ifBlank { page.route }, page.route)))
            }
        },
        onNodeMove = { id, p -> localPositions[id] = p },
        onNodeMoveEnd = {
            val merged = doc.positions + localPositions.mapValues { CanvasPoint(it.value.x, it.value.y) }
            onChange(doc.copy(positions = merged))
        }
    )
}
