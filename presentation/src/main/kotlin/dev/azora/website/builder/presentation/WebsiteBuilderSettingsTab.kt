package dev.azora.website.builder.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.sdk.plugin.core.PluginContext
import dev.azora.website.builder.data.WebSceneFiles
import dev.azora.website.builder.domain.WebsiteConfig
import kotlinx.coroutines.launch

/**
 * The "Website Builder" project-settings tab: pick which `.azn` file is the site's config and which is
 * the navigation, with "Open" shortcuts. Selections are persisted in `project.azora` extras so the
 * generator and other tooling can resolve them.
 */
@Composable
fun WebsiteBuilderSettingsTab(context: PluginContext) {
    val palette = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val fs = context.fileSystem
    val projectPath = context.projectPath

    var configPath by remember(context.project) { mutableStateOf(WebsiteConfig.configPath(context.project) ?: "") }
    var navPath by remember(context.project) { mutableStateOf(WebsiteConfig.navPath(context.project) ?: "") }
    var configCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var navCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(projectPath) {
        configCandidates = WebSceneFiles.configFilePaths(fs, projectPath)
        navCandidates = WebSceneFiles.navigationFilePaths(fs, projectPath)
        // Default to the first candidate if not explicitly set in project extras.
        if (configPath.isBlank() && configCandidates.isNotEmpty()) {
            configPath = configCandidates.first()
            context.saveProject(WebsiteConfig.withConfigPath(context.project, configPath))
        }
        if (navPath.isBlank() && navCandidates.isNotEmpty()) {
            navPath = navCandidates.first()
            context.saveProject(WebsiteConfig.withNavPath(context.project, navPath))
        }
    }

    fun saveConfig(path: String) {
        configPath = path
        context.saveProject(WebsiteConfig.withConfigPath(context.project, path))
    }
    fun saveNav(path: String) {
        navPath = path
        context.saveProject(WebsiteConfig.withNavPath(context.project, path))
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Website Builder", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("Project-level configuration for the website builder.", color = palette.onSurfaceVariant, fontSize = 12.sp)

        HorizontalDivider()
        FilePickerRow(
            label = "Config file (.azn)",
            currentPath = configPath,
            candidates = configCandidates,
            onChange = ::saveConfig,
            onOpen = { if (configPath.isNotBlank()) context.openScene(configPath) }
        )
    }
}

@Composable
private fun FilePickerRow(
    label: String,
    currentPath: String,
    candidates: List<String>,
    onChange: (String) -> Unit,
    onOpen: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val display = currentPath.substringAfterLast('/').ifBlank { "— not set —" }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = palette.onSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    Box {
                        TextButton(onClick = { expanded = true }) { Text("Browse…", fontSize = 11.sp) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            candidates.forEach { path ->
                                DropdownMenuItem(
                                    text = { Text(path.substringAfterLast('/'), fontSize = 12.sp) },
                                    onClick = { onChange(path); expanded = false }
                                )
                            }
                        }
                    }
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            )
            TextButton(onClick = onOpen, enabled = currentPath.isNotBlank()) { Text("Open", fontSize = 11.sp) }
        }
    }
}
