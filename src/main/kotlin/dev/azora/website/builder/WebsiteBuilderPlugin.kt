package dev.azora.website.builder

import androidx.compose.runtime.Composable
import dev.azora.sdk.core.io.FileSystem
import dev.azora.sdk.core.project.domain.AzoraProjectModel
import dev.azora.sdk.core.project.domain.ProjectTemplateContribution
import dev.azora.sdk.core.project.domain.ProjectTemplateGenerator
import dev.azora.sdk.core.project.domain.ProjectRunTarget
import dev.azora.sdk.core.project.domain.ProjectRunTargetKind
import dev.azora.sdk.compiler.scene.domain.SceneColumn
import dev.azora.sdk.compiler.scene.domain.NavLink
import dev.azora.sdk.compiler.scene.domain.SceneFontWeight
import dev.azora.sdk.compiler.scene.domain.SceneModifier
import dev.azora.sdk.compiler.scene.domain.SceneSlot
import dev.azora.sdk.compiler.scene.domain.SceneText
import dev.azora.sdk.plugin.core.AzoraPlugin
import dev.azora.sdk.plugin.core.AzsceneTemplate
import dev.azora.sdk.plugin.core.PluginCategory
import dev.azora.sdk.plugin.core.PluginContext
import dev.azora.sdk.plugin.core.PluginManifest
import dev.azora.sdk.plugin.core.PluginPanelDescriptor
import dev.azora.sdk.plugin.core.SettingsTabDescriptor
import dev.azora.website.builder.presentation.AzsceneEditorScreen
import dev.azora.website.builder.presentation.WebsiteBuilderSettingsTab
import dev.azora.website.builder.presentation.WebsitePreviewPanel
import dev.azora.website.builder.presentation.theme.AzoraMaterialTheme
import dev.azora.website.builder.data.generator.ReactSiteGenerator
import dev.azora.sdk.compiler.scene.domain.SceneDocument
import dev.azora.website.builder.data.WebSceneFiles
import dev.azora.website.builder.domain.WebSceneType

/**
 * Azora plugin for building React websites. It contributes the **Website** project template and the
 * React 19.2 (Vite, JSX + CSS) generator; the actual editing happens in Studio's native `.azscene`
 * editor (double-click a file under `pages/`/`components/`), so this plugin contributes no panel/tab.
 *
 * Studio has no compile dependency on this plugin — it is discovered at runtime from its JAR's
 * `plugin.json` and loaded via the plugin manager.
 */
class WebsiteBuilderPlugin : AzoraPlugin {

    override val metadata: PluginManifest = PluginManifest(
        id = "dev.azora.website_builder",
        name = "Website Builder",
        version = "0.0.1",
        description = "React website generator — edit pages/components as .azn files, generate a Vite app.",
        author = "Azora",
        mainClass = "dev.azora.website.builder.WebsiteBuilderPlugin",
        accentColor = "#FF22C55E",
        iconPath = "icon.xml",
        category = PluginCategory.GENERATOR,
        tags = listOf("website", "react", "web")
    )

    override fun projectTemplates(): List<ProjectTemplateContribution> = listOf(
        ProjectTemplateContribution(
            id = "website",
            label = "Website",
            description = "React 19 website (Vite, JSX + CSS) built visually",
            accentColor = "#FF22C55E",
            generator = WebsiteGenerator(),
            runTargets = listOf(
                ProjectRunTarget(
                    id = "website-dev",
                    label = "Run Website",
                    kind = ProjectRunTargetKind.COMMAND,
                    command = "npm install && npm run dev",
                    workingDir = WebSceneFiles.GENERATED_DIR
                )
            )
        )
    )

    /** The single tab this plugin contributes: a live preview of the site. Editing happens by
     *  double-clicking `.azscene` files (page/component/config) in the file browser. */
    override fun panels(): List<PluginPanelDescriptor> = listOf(
        PluginPanelDescriptor(id = "preview", title = "Website Preview", minimumWidth = 420f, minimumHeight = 320f)
    )

    @Composable
    override fun PanelContent(panelId: String, context: PluginContext) {
        if (panelId == "preview") AzoraMaterialTheme { WebsitePreviewPanel(context) }
    }

    /** Fallback for single-tab hosts: also show the preview. */
    @Composable
    override fun Content(context: PluginContext) {
        AzoraMaterialTheme { WebsitePreviewPanel(context) }
    }

    /** Contributes a "Website Builder" tab to Studio Settings (project-level config/nav pickers). */
    override fun settingsTabs(): List<SettingsTabDescriptor> =
        listOf(SettingsTabDescriptor("website-builder", "Website Builder"))

    @Composable
    override fun settingsTabContent(tabId: String, context: PluginContext) {
        if (tabId == "website-builder") AzoraMaterialTheme { WebsiteBuilderSettingsTab(context) }
    }

    /** The `.azscene` document types this plugin edits. */
    override fun azsceneEditorTypes(): Set<String> = WebSceneType.all

    /** The `.azscene` document types this plugin can create (shown in Studio's "New …" menu). */
    override fun azsceneTemplates(): List<AzsceneTemplate> = listOf(
        AzsceneTemplate(WebSceneType.PAGE, "Website Page"),
        AzsceneTemplate(WebSceneType.COMPONENT, "Website Component"),
        AzsceneTemplate(WebSceneType.NAVIGATION, "Website Navigation"),
        AzsceneTemplate(WebSceneType.CONFIG, "Website Config")
    )

    override fun newAzsceneContent(type: String): String? =
        if (type in WebSceneType.all) WebSceneFiles.newDocJson(type) else null

    /** Studio routes a double-clicked website `.azscene` here based on its `type`. */
    @Composable
    override fun AzsceneEditor(type: String, filePath: String, context: PluginContext) {
        AzoraMaterialTheme { AzsceneEditorScreen(type = type, filePath = filePath, context = context) }
    }

    /**
     * Scaffolds the project layout (only creating the starter page if `pages/` is empty, so it is
     * safe to re-run as a "Generate"), then emits the React app into `generated/`.
     */
    private class WebsiteGenerator : ProjectTemplateGenerator {
        override suspend fun generate(project: AzoraProjectModel, projectPath: String, fileSystem: FileSystem) {
            val brand = project.name.ifBlank { "Azora Site" }
            fileSystem.createDirectory("$projectPath/${WebSceneFiles.COMPONENTS_DIR}")

            if (WebSceneFiles.loadPages(fileSystem, projectPath).isEmpty()) {
                // Scaffold three starter pages with working routes.
                data class StarterPage(val name: String, val route: String, val heading: String, val body: String)
                listOf(
                    StarterPage("Home", "/", "Welcome to $brand", "Built visually with Azora Studio."),
                    StarterPage("About", "/about", "About $brand", "Learn more about what we do."),
                    StarterPage("ContactUs", "/contact-us", "Contact Us", "Get in touch — we'd love to hear from you.")
                ).forEach { sp ->
                    val heading = SceneText(text = sp.heading, modifier = SceneModifier(fontSize = 40, fontWeight = SceneFontWeight.BOLD))
                    val body = SceneText(text = sp.body, modifier = SceneModifier(fontSize = 18, textColor = "#9CA3AF"))
                    val root = SceneColumn(
                        modifier = SceneModifier(fillMaxWidth = true, padding = 48, gap = 16),
                        slots = listOf(SceneSlot(childId = heading.id), SceneSlot(childId = body.id))
                    )
                    WebSceneFiles.writePage(fileSystem, projectPath, sp.name, SceneDocument(
                        type = WebSceneType.PAGE, name = sp.name, route = sp.route,
                        nodes = listOf(root, heading, body), rootId = root.id
                    ))
                }
            }

            // Site config scene (WebsiteConfig.azn), created once.
            if (WebSceneFiles.readConfig(fileSystem, projectPath) == null) {
                WebSceneFiles.writeConfig(fileSystem, projectPath, SceneDocument(type = WebSceneType.CONFIG, name = "WebsiteConfig",
                    settings = mapOf(
                        "title" to brand, "description" to "Built with Azora Studio", "themeColor" to "#D14EEA",
                        "navFile" to "WebsiteNavigation${WebSceneFiles.EXT}"
                    )))
            }

            // Navigation scene (WebsiteNavigation.azn), created once with Home/About/Contact entries.
            if (WebSceneFiles.loadNavigation(fileSystem, projectPath).isEmpty()) {
                WebSceneFiles.write(fileSystem, "$projectPath/WebsiteNavigation${WebSceneFiles.EXT}",
                    SceneDocument(type = WebSceneType.NAVIGATION, name = "WebsiteNavigation",
                        nav = listOf(NavLink("Home", "/"), NavLink("About", "/about"), NavLink("Contact Us", "/contact-us"))))
            }

            ReactSiteGenerator(fileSystem).generate(projectPath, project.name, brand)
        }
    }
}
