package dev.azora.website.builder.data.generator

import dev.azora.sdk.core.io.FileSystem
import dev.azora.sdk.core.project.domain.CodeGenerator
import dev.azora.sdk.core.project.domain.CodeGeneratorImpl
import dev.azora.website.builder.domain.WebComponent
import dev.azora.website.builder.domain.WebColumn
import dev.azora.website.builder.domain.WebRow
import dev.azora.website.builder.domain.WebBox
import dev.azora.website.builder.domain.WebButton
import dev.azora.website.builder.domain.WebSceneDoc
import dev.azora.website.builder.domain.WebSlot
import dev.azora.website.builder.data.WebSceneFiles

/**
 * Generates a runnable Vite + React 19.2 app into `<project>/generated/` from the project's
 * `pages/` and `components/` `.azscene` files. Component instances become real React child
 * components, so editing a component and regenerating updates everywhere it is used.
 *
 * Only the React project is written — no Gradle/Kotlin files. Run it with `npm install && npm run dev`.
 */
class ReactSiteGenerator(private val fileSystem: FileSystem) {

    suspend fun generate(projectPath: String, appName: String, title: String) {
        val app = sanitizeAppName(appName)
        val gen = "$projectPath/${WebSceneFiles.GENERATED_DIR}"

        val pages = WebSceneFiles.loadPages(fileSystem, projectPath)
        val components = WebSceneFiles.loadComponents(fileSystem, projectPath)

        // Component name -> React component/file name, for resolving instances during emission.
        val reactNames: Map<String, String> = components.associate { it.name to pascal(it.name) }

        // ----- static scaffolding -----
        fileSystem.writeToFile("$gen/package.json", ReactProjectFiles.packageJson(app))
        fileSystem.writeToFile("$gen/vite.config.js", ReactProjectFiles.viteConfig)
        fileSystem.writeToFile("$gen/index.html", ReactProjectFiles.indexHtml(title))
        fileSystem.writeToFile("$gen/src/main.jsx", ReactProjectFiles.mainJsx)
        fileSystem.writeToFile("$gen/src/index.css", ReactProjectFiles.indexCss)
        // Our reusable custom button component (used instead of a native <button>).
        fileSystem.writeToFile("$gen/src/components/AzButton.jsx", ReactProjectFiles.azButtonJsx)

        // ----- components -----
        components.forEach { doc ->
            val name = pascal(doc.name)
            fileSystem.writeToFile("$gen/src/components/$name.jsx", module(name, doc, reactNames))
            fileSystem.writeToFile("$gen/src/components/$name.css", CssEmitter.fileCss(doc.rootId, doc.nodes, doc.instances))
        }

        // ----- pages -----
        val routePages = pages.map { doc ->
            val name = pascal(doc.name)
            fileSystem.writeToFile("$gen/src/pages/$name.jsx", module(name, doc, reactNames))
            fileSystem.writeToFile("$gen/src/pages/$name.css", CssEmitter.fileCss(doc.rootId, doc.nodes, doc.instances))
            RoutePage(componentName = name, route = doc.route.ifBlank { "/" }, isHome = doc.route.isBlank() || doc.route == "/")
        }
        fileSystem.writeToFile("$gen/src/App.jsx", ReactProjectFiles.appJsx(routePages))
    }

    /** Builds one `.jsx` module: css import, child-component imports, and the default-exported function. */
    private fun module(name: String, doc: WebSceneDoc, reactNames: Map<String, String>): String {
        val pool = doc.nodes.associateBy { it.id }
        val root = pool[doc.rootId]
        val imports = doc.instances.values.toSet().mapNotNull { reactNames[it] }.distinct().sorted()
        return buildSource {
            write("import './$name.css'")
            if (root != null && treeHasButton(root, pool)) write("import AzButton from '../components/AzButton.jsx'")
            imports.forEach { write("import $it from '../components/$it.jsx'") }
            blank()
            write("export default function $name() {")
            gen {
                write("return (")
                if (root != null) gen { JsxEmitter.emit(this, root, pool, doc.instances, reactNames) }
                else write("<div />")
                write(")")
            }
            write("}")
        }
    }

    /** Whether [root]'s reachable graph contains a button node (instances are separate modules that
     *  import [AzButton] themselves), so the module knows to import the AzButton component. `visiting`
     *  guards against cycles. */
    private fun treeHasButton(root: WebComponent, pool: Map<String, WebComponent>, visiting: Set<String> = emptySet()): Boolean {
        if (root.id in visiting) return false
        return when (root) {
            is WebButton -> true
            is WebColumn -> root.slots.anySlotChild(pool) { treeHasButton(it, pool, visiting + root.id) }
            is WebRow -> root.slots.anySlotChild(pool) { treeHasButton(it, pool, visiting + root.id) }
            is WebBox -> root.slots.anySlotChild(pool) { treeHasButton(it, pool, visiting + root.id) }
            else -> false
        }
    }

    /** True if any occupied slot of this container resolves (via [pool]) to a node satisfying [pred]. */
    private fun List<WebSlot>.anySlotChild(pool: Map<String, WebComponent>, pred: (WebComponent) -> Boolean): Boolean =
        any { s -> s.childId?.let { pool[it] }?.let(pred) == true }

    private fun sanitizeAppName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() || it == '-' }.trim('-').ifBlank { "site" }

    companion object {
        /** PascalCase identifier for a React component/file name. */
        fun pascal(name: String): String {
            val parts = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
            val joined = parts.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            val s = joined.ifBlank { "Component" }
            return if (s.first().isDigit()) "C$s" else s
        }
    }
}

/** Renders code through the SDK [CodeGenerator] DSL (handles indentation). */
internal fun buildSource(block: CodeGenerator.GenScope.() -> Unit): String =
    CodeGeneratorImpl().gen(block).build()
