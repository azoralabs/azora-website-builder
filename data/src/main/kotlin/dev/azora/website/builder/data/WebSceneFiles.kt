package dev.azora.website.builder.data

import dev.azora.sdk.compiler.scene.data.SceneFiles
import dev.azora.sdk.compiler.scene.domain.SceneDocument
import dev.azora.sdk.core.io.FileSystem
import dev.azora.website.builder.domain.WebSceneType

/**
 * Website-builder conventions over the SDK's universal [SceneFiles]: the `pages/`/`components/`
 * folder layout written by the scaffold, the `WebsiteConfig.azn` config file, and typed loaders
 * for the website scene types ([WebSceneType]). All actual `.azn` discovery/reading/writing —
 * including the legacy-format migration — lives in the SDK.
 */
object WebSceneFiles {

    const val PAGES_DIR = "pages"
    const val COMPONENTS_DIR = "components"
    const val GENERATED_DIR = SceneFiles.GENERATED_DIR
    const val EXT = SceneFiles.EXT
    const val CONFIG_FILE = "WebsiteConfig$EXT"
    /** Legacy config file name (pre-rename) — still read as a fallback. */
    const val CONFIG_FILE_LEGACY = "website$EXT"

    val json = SceneFiles.json

    fun fileName(name: String): String = SceneFiles.fileName(name)

    /** Default JSON content for a brand-new `.azn` document of [type]. PAGE/COMPONENT docs get a
     *  root container so there's something to wire nodes into. */
    fun newDocJson(type: String): String =
        SceneFiles.newDocJson(type, withRootContainer = type == WebSceneType.PAGE || type == WebSceneType.COMPONENT)

    /** Conventional locations; used only by the initial project scaffold (the writers below). */
    fun pagePath(projectPath: String, name: String) = "$projectPath/$PAGES_DIR/$name$EXT"
    fun componentPath(projectPath: String, name: String) = "$projectPath/$COMPONENTS_DIR/$name$EXT"
    fun configPath(projectPath: String) = "$projectPath/$CONFIG_FILE"

    /** Absolute paths of every `.azn` file anywhere under the project (folders are conventional,
     *  not enforced; heavy/build dirs are pruned). */
    suspend fun listAllAzscenePaths(fs: FileSystem, projectPath: String): List<String> =
        SceneFiles.listAllScenePaths(fs, projectPath)

    suspend fun read(fs: FileSystem, path: String): SceneDocument? = SceneFiles.read(fs, path)

    suspend fun write(fs: FileSystem, path: String, doc: SceneDocument) = SceneFiles.write(fs, path, doc)

    /** Reads the config doc from the new `WebsiteConfig.azn`, falling back to the legacy `website.azn`
     *  for projects created before the rename. */
    suspend fun readConfig(fs: FileSystem, projectPath: String): SceneDocument? {
        read(fs, configPath(projectPath))?.let { return it }
        return read(fs, "$projectPath/$CONFIG_FILE_LEGACY")
    }

    suspend fun writePage(fs: FileSystem, projectPath: String, name: String, doc: SceneDocument) =
        write(fs, pagePath(projectPath, name), doc)
    suspend fun writeComponent(fs: FileSystem, projectPath: String, name: String, doc: SceneDocument) =
        write(fs, componentPath(projectPath, name), doc)
    suspend fun writeConfig(fs: FileSystem, projectPath: String, doc: SceneDocument) =
        write(fs, configPath(projectPath), doc)

    /** Every PAGE document in the project, wherever its file lives. */
    suspend fun loadPages(fs: FileSystem, projectPath: String): List<SceneDocument> =
        SceneFiles.loadByType(fs, projectPath, WebSceneType.PAGE)

    /** Every COMPONENT document in the project (instance library + generation input). */
    suspend fun loadComponents(fs: FileSystem, projectPath: String): List<SceneDocument> =
        SceneFiles.loadByType(fs, projectPath, WebSceneType.COMPONENT)

    /** Every NAVIGATION document in the project (the site's nav-bar definition). */
    suspend fun loadNavigation(fs: FileSystem, projectPath: String): List<SceneDocument> =
        SceneFiles.loadByType(fs, projectPath, WebSceneType.NAVIGATION)

    /** File paths of NAVIGATION-type `.azn` documents only — for the nav-file picker dropdown. */
    suspend fun navigationFilePaths(fs: FileSystem, projectPath: String): List<String> =
        SceneFiles.pathsByType(fs, projectPath, WebSceneType.NAVIGATION)

    /** File paths of CONFIG-type `.azn` documents only — for the config-file picker dropdown. */
    suspend fun configFilePaths(fs: FileSystem, projectPath: String): List<String> =
        SceneFiles.pathsByType(fs, projectPath, WebSceneType.CONFIG)

    /** Absolute path of the COMPONENT document identified by [name], wherever it lives. */
    suspend fun findComponentPath(fs: FileSystem, projectPath: String, name: String): String? =
        SceneFiles.findPathByTypeAndName(fs, projectPath, WebSceneType.COMPONENT, name)
}