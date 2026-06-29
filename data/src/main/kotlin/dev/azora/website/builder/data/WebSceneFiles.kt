package dev.azora.website.builder.data
import dev.azora.website.builder.domain.WebSceneDoc
import dev.azora.website.builder.domain.WebSceneType

import dev.azora.sdk.core.io.FileReadResult
import dev.azora.sdk.core.io.FileSystem
import dev.azora.sdk.core.io.ListResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads/writes the website project's `.azscene` documents and the React output directory.
 *
 * `.azscene` files may live **anywhere** in the project — the `pages/` and `components/` folders are
 * only a human-readable convention, not a requirement. Documents are therefore discovered by
 * scanning the whole project tree and classifying each file by its in-doc [WebSceneDoc.type]
 * (`PAGE`, `COMPONENT`, `CONFIG`, …), never by their location:
 *
 * ```
 * <project>/**/*.azscene     discovered by type, wherever the file lives
 * <project>/website.azscene  the CONFIG scene (conventional location written by the scaffold)
 * <project>/generated/       emitted React app (skipped during discovery)
 * ```
 *
 * A file's base name (sans extension) is each page/component's identity: instance references and
 * the generated React file/import names all key off it.
 */
object WebSceneFiles {

    const val PAGES_DIR = "pages"
    const val COMPONENTS_DIR = "components"
    const val GENERATED_DIR = "generated"
    const val EXT = ".azn"
    const val CONFIG_FILE = "WebsiteConfig$EXT"
    /** Legacy config file name (pre-rename) — still read as a fallback. */
    const val CONFIG_FILE_LEGACY = "website$EXT"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun fileName(name: String): String =
        name.trim().map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").trim('_').ifBlank { "Untitled" }

    /** Default JSON content for a brand-new `.azscene` document of [type]. PAGE/COMPONENT docs get a
     *  root container so there's something to wire nodes into. */
    fun newDocJson(type: String): String =
        json.encodeToString(
            if (type == WebSceneType.PAGE || type == WebSceneType.COMPONENT) WebSceneDoc.withRoot(type = type)
            else WebSceneDoc(type = type)
        )

    /** Conventional locations; used only by the initial project scaffold (the writers below). */
    fun pagePath(projectPath: String, name: String) = "$projectPath/$PAGES_DIR/$name$EXT"
    fun componentPath(projectPath: String, name: String) = "$projectPath/$COMPONENTS_DIR/$name$EXT"
    fun configPath(projectPath: String) = "$projectPath/$CONFIG_FILE"

    /** Absolute paths of every `.azscene` file anywhere under the project (folders are conventional,
     *  not enforced). Descends manually so heavy/build dirs (`generated/`, `node_modules`, `build`,
     *  `.git`, …) are pruned before being listed — important because this runs on a poll. */
    suspend fun listAllAzscenePaths(fs: FileSystem, projectPath: String): List<String> {
        val out = mutableListOf<String>()
        val skipDirs = setOf(GENERATED_DIR, "node_modules", "build", ".git", ".gradle", ".idea")
        suspend fun walk(dir: String) {
            when (val r = fs.listDirectory(dir)) {
                is ListResult.Success -> r.files.forEach { f ->
                    when {
                        f.isDirectory -> if (f.name !in skipDirs && !f.name.startsWith(".")) walk(f.path)
                        f.name.endsWith(EXT) -> out += f.path
                    }
                }
                is ListResult.Error -> {}
            }
        }
        walk(projectPath)
        return out
    }

    suspend fun read(fs: FileSystem, path: String): WebSceneDoc? {
        // The file's base name is the component/page identity (instance references, generated
        // React file/import names all key off it). The in-doc `name` is empty for documents
        // created via newDocJson until they're opened and saved, so fall back to the base name
        // here — otherwise such components render as blank rows in the instance dropdown and
        // collide in generation.
        val baseName = path.substringAfterLast('/').removeSuffix(EXT)
        return when (val r = fs.readFromFile(path)) {
            is FileReadResult.Success -> runCatching {
                json.decodeFromString<WebSceneDoc>(migrateLegacyJson(r.content))
                    .let { if (it.name.isBlank()) it.copy(name = baseName) else it }
            }.getOrNull()
            is FileReadResult.Error -> null
        }
    }

    /**
     * Converts a legacy `.azn` JSON (parent-child `root` tree + `freeNodes`, inline `children`) into
     * the pool model (`nodes` + `rootId`, containers hold `slots` referencing child ids). No-op for
     * files already in the new shape (no `"root"` key). Operates on the JSON DOM so we don't need a
     * duplicate Kotlin model of the old layout.
     */
    internal fun migrateLegacyJson(content: String): String {
        val parsed = runCatching { json.parseToJsonElement(content) }.getOrNull() ?: return content
        val obj = parsed.jsonObject
        val rootEl = obj["root"] ?: return content // already pool-shaped
        val containerTypes = setOf("column", "row", "box")
        val collected = LinkedHashMap<String, JsonObject>()

        fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.content

        fun visit(nodeEl: JsonElement) {
            val node = nodeEl.jsonObject
            val id = node["id"].str() ?: return
            if (collected.containsKey(id)) return
            if (node["type"].str() in containerTypes) {
                val children = (node["children"] as? JsonArray) ?: emptyList()
                val emptySlotIds = (node["emptySlotIds"] as? JsonArray) ?: emptyList()
                val slots = buildJsonArray {
                    children.forEachIndexed { i, child ->
                        child.jsonObject["id"].str()?.let { cid ->
                            add(buildJsonObject {
                                put("id", JsonPrimitive("s_${id}_$i"))
                                put("childId", JsonPrimitive(cid))
                            })
                        }
                    }
                    emptySlotIds.forEach { sid ->
                        sid.str()?.let { sidStr ->
                            add(buildJsonObject {
                                put("id", JsonPrimitive(sidStr))
                                put("childId", JsonNull)
                            })
                        }
                    }
                }
                collected[id] = buildJsonObject {
                    node.forEach { (k, v) -> if (k != "children" && k != "emptySlotIds") put(k, v) }
                    put("slots", slots)
                }
                children.forEach { visit(it) }
            } else {
                collected[id] = node
            }
        }

        visit(rootEl)
        (obj["freeNodes"] as? JsonArray)?.forEach { visit(it) }
        val rootId = rootEl.jsonObject["id"].str() ?: ""

        return buildJsonObject {
            obj.forEach { (k, v) -> if (k != "root" && k != "freeNodes") put(k, v) }
            put("nodes", buildJsonArray { collected.values.forEach { add(it) } })
            put("rootId", JsonPrimitive(rootId))
        }.toString()
    }

    suspend fun write(fs: FileSystem, path: String, doc: WebSceneDoc) {
        fs.writeToFile(path, json.encodeToString(doc))
    }

    /** Reads the config doc from the new `WebsiteConfig.azn`, falling back to the legacy `website.azn`
     *  for projects created before the rename. */
    suspend fun readConfig(fs: FileSystem, projectPath: String): WebSceneDoc? {
        read(fs, configPath(projectPath))?.let { return it }
        return read(fs, "$projectPath/$CONFIG_FILE_LEGACY")
    }
    suspend fun writePage(fs: FileSystem, projectPath: String, name: String, doc: WebSceneDoc) =
        write(fs, pagePath(projectPath, name), doc)
    suspend fun writeComponent(fs: FileSystem, projectPath: String, name: String, doc: WebSceneDoc) =
        write(fs, componentPath(projectPath, name), doc)
    suspend fun writeConfig(fs: FileSystem, projectPath: String, doc: WebSceneDoc) =
        write(fs, configPath(projectPath), doc)

    /** Every PAGE document in the project, wherever its file lives. */
    suspend fun loadPages(fs: FileSystem, projectPath: String): List<WebSceneDoc> =
        listAllAzscenePaths(fs, projectPath).mapNotNull { read(fs, it) }
            .filter { it.type == WebSceneType.PAGE }

    /** Every COMPONENT document in the project (instance library + generation input). */
    suspend fun loadComponents(fs: FileSystem, projectPath: String): List<WebSceneDoc> =
        listAllAzscenePaths(fs, projectPath).mapNotNull { read(fs, it) }
            .filter { it.type == WebSceneType.COMPONENT }

    /** Every NAVIGATION document in the project (the site's nav-bar definition). */
    suspend fun loadNavigation(fs: FileSystem, projectPath: String): List<WebSceneDoc> =
        listAllAzscenePaths(fs, projectPath).mapNotNull { read(fs, it) }
            .filter { it.type == WebSceneType.NAVIGATION }

    /** Absolute path of the COMPONENT document identified by [name] (its in-doc name or file base
     *  name), wherever it lives in the project — components are discovered by scanning, not by their
     *  folder, so the conventional `components/<name>.azn` may not be where it actually is. Null if
     *  no such component exists. */
    suspend fun findComponentPath(fs: FileSystem, projectPath: String, name: String): String? =
        listAllAzscenePaths(fs, projectPath).firstOrNull { path ->
            val doc = read(fs, path)
            doc != null && doc.type == WebSceneType.COMPONENT &&
                (doc.name == name || path.substringAfterLast('/').removeSuffix(EXT) == name)
        }
}
