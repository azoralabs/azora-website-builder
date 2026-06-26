package dev.azora.website.builder.presentation
import dev.azora.website.builder.domain.WebComponentTree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.sdk.color.ColorPicker
import dev.azora.sdk.core.component.button.AzoraButton
import dev.azora.sdk.core.component.textfield.AzoraTextField
import dev.azora.website.builder.domain.*
import kotlin.math.roundToInt

/**
 * Inspector for the selected [WebComponent], styled after a Jetpack-Compose **modifier chain**: an
 * "Add modifier" menu and a list of removable, collapsible modifier cards. Each modifier maps to a
 * [WebModifier] field (→ CSS at generation). Built from Azora SDK widgets ([ColorPicker],
 * [AzoraTextField], [AzoraButton]).
 */
@Composable
fun ComponentPropertiesPanel(
    selected: WebComponent?,
    isRoot: Boolean,
    onChange: (WebComponent) -> Unit,
    onDelete: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxHeight().width(286.dp)
            .background(palette.surface)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selected == null) {
            Text("Select a node to edit its properties.", color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            return@Column
        }

        Text(WebComponentTree.typeLabel(selected), color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        // ---- Content (per node type) ----
        if (hasContent(selected)) {
            when (selected) {
                is WebText -> textField("text", selected.text) { onChange(selected.copy(text = it)) }
                is WebButton -> textField("label", selected.label) { onChange(selected.copy(label = it)) }
                is WebLink -> {
                    textField("text", selected.text) { onChange(selected.copy(text = it)) }
                    textField("href", selected.href) { onChange(selected.copy(href = it)) }
                }
                is WebImage -> {
                    textField("src", selected.src) { onChange(selected.copy(src = it)) }
                    textField("alt", selected.alt) { onChange(selected.copy(alt = it)) }
                }
                is WebInput -> textField("placeholder", selected.placeholder) { onChange(selected.copy(placeholder = it)) }
                else -> {}
            }
            HorizontalDivider(color = palette.outlineVariant)
        }

        // ---- Modifier chain ----
        val active = WebMod.entries.filter { it.applies(selected) && it.isActive(selected) }
        val addable = WebMod.entries.filter { it.applies(selected) && !it.isActive(selected) }

        var showAdd by remember(selected.id) { mutableStateOf(false) }
        AddModifierButton(enabled = addable.isNotEmpty()) { showAdd = !showAdd }
        if (showAdd && addable.isNotEmpty()) {
            AddModifierMenu(addable) { mod -> onChange(mod.add(selected)); showAdd = false }
        }
        if (active.isEmpty()) {
            Text(".Modifier", color = palette.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("No modifiers yet — add one above.", color = palette.onSurfaceVariant, fontSize = 11.sp)
        }
        active.forEach { mod ->
            ModifierCard(mod = mod, component = selected, onChange = onChange, onRemove = { onChange(mod.remove(selected)) })
        }

        if (!isRoot) {
            HorizontalDivider(color = palette.outlineVariant)
            TextButton(onClick = onDelete) { Text("Delete node", color = palette.error) }
        }
    }
}

// ---------------- modifier card ----------------

@Composable
private fun ModifierCard(
    mod: WebMod,
    component: WebComponent,
    onChange: (WebComponent) -> Unit,
    onRemove: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val meta = mod.meta()
    var expanded by remember(component.id, mod) { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.outlineVariant, RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(meta.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Text(meta.icon, color = meta.accent, fontSize = 13.sp) }
            Text(meta.name, color = palette.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(if (expanded) "▾" else "▸", color = palette.onSurfaceVariant, fontSize = 10.sp)
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(palette.surface).clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) { Text("×", color = palette.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModifierEditor(mod, component, onChange)
            }
        }
    }
}

@Composable
private fun ModifierEditor(mod: WebMod, c: WebComponent, onChange: (WebComponent) -> Unit) {
    val m = c.modifier
    fun set(mod2: WebModifier) = onChange(c.withModifier(mod2))
    when (mod) {
        WebMod.SIZE -> {
            PropertyRow("width") { NumberInput(m.width ?: 0, min = 1) { set(m.copy(width = it)) } }
            PropertyRow("height") { NumberInput(m.height ?: 0, min = 1) { set(m.copy(height = it)) } }
            PropertyRow("fillMaxWidth") { Toggle(m.fillMaxWidth) { set(m.copy(fillMaxWidth = it)) } }
            PropertyRow("fillMaxHeight") { Toggle(m.fillMaxHeight) { set(m.copy(fillMaxHeight = it)) } }
        }
        WebMod.PADDING -> PropertyRow("all") { NumberInput(m.padding ?: 0) { set(m.copy(padding = it.coerceAtLeast(0))) } }
        WebMod.SPACED_BY -> PropertyRow("space") { NumberInput(m.gap ?: 0) { set(m.copy(gap = it.coerceAtLeast(0))) } }
        WebMod.ARRANGEMENT -> Chips(arrangementOptions, c.arrangementOrNull() ?: WebArrangement.START) { onChange(c.withArrangement(it)) }
        WebMod.BACKGROUND -> colorField("color", m.backgroundColor) { set(m.copy(backgroundColor = it)) }
        WebMod.BORDER -> {
            PropertyRow("width") { NumberInput(m.borderWidth ?: 0, min = 1) { set(m.copy(borderWidth = it)) } }
            colorField("color", m.borderColor) { set(m.copy(borderColor = it)) }
        }
        WebMod.CORNER_RADIUS -> PropertyRow("radius") { NumberInput(m.cornerRadius ?: 0) { set(m.copy(cornerRadius = it.coerceAtLeast(0))) } }
        WebMod.TEXT_COLOR -> colorField("color", m.textColor) { set(m.copy(textColor = it)) }
        WebMod.FONT_SIZE -> PropertyRow("size") { NumberInput(m.fontSize ?: 0, "px", min = 1) { set(m.copy(fontSize = it)) } }
        WebMod.FONT_WEIGHT -> Chips(fontWeightOptions, m.fontWeight) { set(m.copy(fontWeight = it)) }
        WebMod.TEXT_ALIGN -> Chips(textAlignOptions, m.textAlign) { set(m.copy(textAlign = it)) }
        WebMod.OPACITY -> PropertyRow("alpha") { NumberInput(m.opacity ?: 100, "%") { set(m.copy(opacity = it.coerceIn(0, 100))) } }
    }
}

// ---------------- add modifier ----------------

@Composable
private fun AddModifierButton(enabled: Boolean, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val tint = if (enabled) palette.primary else palette.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text("+ Add modifier", color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddModifierMenu(addable: List<WebMod>, onPick: (WebMod) -> Unit) {
    val palette = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.outlineVariant, RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        addable.forEach { mod ->
            val meta = mod.meta()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onPick(mod) }.padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(meta.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(meta.icon, color = meta.accent, fontSize = 12.sp)
                }
                Text(meta.name, color = palette.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ---------------- field widgets ----------------

@Composable
private fun PropertyRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** ave-style stepper: − [editable] +  with a unit suffix.
 *
 *  Holds a local text buffer so clearing the field to retype (or typing a partial value) does NOT
 *  push a transient 0/blank to the model — that would null the field and collapse the whole
 *  modifier card mid-edit (the card's visibility is derived from the field value). Only a parsed
 *  integer >= [min] is committed; a blank/invalid buffer keeps the model's last value, so the card
 *  stays put until its × button removes it. */
@Composable
private fun NumberInput(value: Int, suffix: String = "dp", min: Int = 0, onValueChange: (Int) -> Unit) {
    val palette = MaterialTheme.colorScheme
    // Keyed on `value` so the buffer resyncs whenever the model changes (our own commit or an
    // external one); while the user is mid-edit the buffer is allowed to differ from `value`.
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(palette.surface).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("−", color = palette.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onValueChange((value - 1).coerceAtLeast(min)) })
        BasicTextField(
            value = text,
            onValueChange = { t ->
                text = t
                val n = t.filter(Char::isDigit).toIntOrNull()
                if (n != null && n >= min) onValueChange(n)
            },
            singleLine = true,
            textStyle = TextStyle(color = palette.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(palette.primary),
            modifier = Modifier.width(34.dp)
        )
        Text(suffix, color = palette.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("+", color = palette.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onValueChange(value + 1) })
    }
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.scale(0.7f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, fontSize = 11.sp) })
        }
    }
}

@Composable
private fun textField(label: String, value: String, onChange: (String) -> Unit) {
    AzoraTextField(value = value, onValueChange = onChange, title = label, singleLine = true, modifier = Modifier.fillMaxWidth())
}

/** Color swatch + inline [ColorPicker] (toggled open); writes `#RRGGBB` (or null to clear). */
@Composable
private fun colorField(label: String, hex: String?, onChange: (String?) -> Unit) {
    val palette = MaterialTheme.colorScheme
    var open by remember(hex) { mutableStateOf(false) }
    val color = parseColorOrNull(hex)
    PropertyRow(label) {
        Text(hex ?: "none", color = palette.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(color ?: palette.surface)
                .border(1.dp, palette.outline, RoundedCornerShape(5.dp)).clickable { open = !open },
            contentAlignment = Alignment.Center
        ) { if (color == null) Text("+", color = palette.onSurfaceVariant, fontSize = 12.sp) }
    }
    if (open) {
        Surface(shape = RoundedCornerShape(8.dp), color = palette.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPicker(currentColor = color ?: Color.White, onColorSelected = { onChange(it.toHex6()) }, wheelSize = 148.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChange(null); open = false }) { Text("clear", fontSize = 12.sp) }
                    AzoraButton(text = "Done", onClick = { open = false }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------- modifier kinds ----------------

private enum class WebMod {
    SIZE, PADDING, SPACED_BY, ARRANGEMENT, BACKGROUND, BORDER, CORNER_RADIUS, TEXT_COLOR, FONT_SIZE, FONT_WEIGHT, TEXT_ALIGN, OPACITY
}

private data class ModMeta(val name: String, val icon: String, val accent: Color)

private fun WebMod.meta(): ModMeta = when (this) {
    WebMod.SIZE -> ModMeta("size", "▢", Color(0xFF3B82F6))
    WebMod.PADDING -> ModMeta("padding", "▥", Color(0xFF14B8A6))
    WebMod.SPACED_BY -> ModMeta("spacedBy", "↕", Color(0xFF14B8A6))
    WebMod.ARRANGEMENT -> ModMeta("arrangement", "≑", Color(0xFF3B82F6))
    WebMod.BACKGROUND -> ModMeta("background", "◧", Color(0xFFA855F7))
    WebMod.BORDER -> ModMeta("border", "▣", Color(0xFFF59E0B))
    WebMod.CORNER_RADIUS -> ModMeta("clip", "◜", Color(0xFFF59E0B))
    WebMod.TEXT_COLOR -> ModMeta("color", "A", Color(0xFFEC4899))
    WebMod.FONT_SIZE -> ModMeta("fontSize", "T", Color(0xFF14B8A6))
    WebMod.FONT_WEIGHT -> ModMeta("fontWeight", "B", Color(0xFF14B8A6))
    WebMod.TEXT_ALIGN -> ModMeta("textAlign", "≡", Color(0xFF14B8A6))
    WebMod.OPACITY -> ModMeta("alpha", "◐", Color(0xFF6B7280))
}

private fun WebMod.applies(c: WebComponent): Boolean = when (this) {
    WebMod.SPACED_BY -> c.isContainer()
    WebMod.ARRANGEMENT -> c is WebColumn || c is WebRow
    else -> true
}

private fun WebMod.isActive(c: WebComponent): Boolean {
    val m = c.modifier
    return when (this) {
        WebMod.SIZE -> m.fillMaxWidth || m.fillMaxHeight || m.width != null || m.height != null
        WebMod.PADDING -> m.padding != null
        WebMod.SPACED_BY -> m.gap != null
        WebMod.ARRANGEMENT -> (c.arrangementOrNull() ?: WebArrangement.START) != WebArrangement.START
        WebMod.BACKGROUND -> m.backgroundColor != null
        WebMod.BORDER -> m.borderWidth != null
        WebMod.CORNER_RADIUS -> m.cornerRadius != null
        WebMod.TEXT_COLOR -> m.textColor != null
        WebMod.FONT_SIZE -> m.fontSize != null
        WebMod.FONT_WEIGHT -> m.fontWeight != WebFontWeight.NORMAL
        WebMod.TEXT_ALIGN -> m.textAlign != WebTextAlign.START
        WebMod.OPACITY -> m.opacity != null
    }
}

private fun WebMod.add(c: WebComponent): WebComponent {
    val m = c.modifier
    return when (this) {
        WebMod.SIZE -> c.withModifier(m.copy(fillMaxWidth = true))
        WebMod.PADDING -> c.withModifier(m.copy(padding = 16))
        WebMod.SPACED_BY -> c.withModifier(m.copy(gap = 8))
        WebMod.ARRANGEMENT -> c.withArrangement(WebArrangement.CENTER)
        WebMod.BACKGROUND -> c.withModifier(m.copy(backgroundColor = "#FFFFFF"))
        WebMod.BORDER -> c.withModifier(m.copy(borderWidth = 1, borderColor = "#000000"))
        WebMod.CORNER_RADIUS -> c.withModifier(m.copy(cornerRadius = 8))
        WebMod.TEXT_COLOR -> c.withModifier(m.copy(textColor = "#000000"))
        WebMod.FONT_SIZE -> c.withModifier(m.copy(fontSize = 16))
        WebMod.FONT_WEIGHT -> c.withModifier(m.copy(fontWeight = WebFontWeight.MEDIUM))
        WebMod.TEXT_ALIGN -> c.withModifier(m.copy(textAlign = WebTextAlign.CENTER))
        WebMod.OPACITY -> c.withModifier(m.copy(opacity = 100))
    }
}

private fun WebMod.remove(c: WebComponent): WebComponent {
    val m = c.modifier
    return when (this) {
        WebMod.SIZE -> c.withModifier(m.copy(fillMaxWidth = false, fillMaxHeight = false, width = null, height = null))
        WebMod.PADDING -> c.withModifier(m.copy(padding = null))
        WebMod.SPACED_BY -> c.withModifier(m.copy(gap = null))
        WebMod.ARRANGEMENT -> c.withArrangement(WebArrangement.START)
        WebMod.BACKGROUND -> c.withModifier(m.copy(backgroundColor = null))
        WebMod.BORDER -> c.withModifier(m.copy(borderWidth = null, borderColor = null))
        WebMod.CORNER_RADIUS -> c.withModifier(m.copy(cornerRadius = null))
        WebMod.TEXT_COLOR -> c.withModifier(m.copy(textColor = null))
        WebMod.FONT_SIZE -> c.withModifier(m.copy(fontSize = null))
        WebMod.FONT_WEIGHT -> c.withModifier(m.copy(fontWeight = WebFontWeight.NORMAL))
        WebMod.TEXT_ALIGN -> c.withModifier(m.copy(textAlign = WebTextAlign.START))
        WebMod.OPACITY -> c.withModifier(m.copy(opacity = null))
    }
}

// ---------------- enum option tables ----------------

private val arrangementOptions = listOf(
    WebArrangement.START to "Start", WebArrangement.CENTER to "Center",
    WebArrangement.END to "End", WebArrangement.SPACE_BETWEEN to "SpaceBetween"
)
private val fontWeightOptions = listOf(
    WebFontWeight.NORMAL to "Normal", WebFontWeight.MEDIUM to "Medium",
    WebFontWeight.SEMI_BOLD to "SemiBold", WebFontWeight.BOLD to "Bold"
)
private val textAlignOptions = listOf(
    WebTextAlign.START to "Start", WebTextAlign.CENTER to "Center", WebTextAlign.END to "End"
)

// ---------------- helpers ----------------

private fun hasContent(c: WebComponent): Boolean =
    c is WebText || c is WebButton || c is WebLink || c is WebImage || c is WebInput

private fun WebComponent.isContainer(): Boolean = this is WebColumn || this is WebRow || this is WebBox

private fun WebComponent.arrangementOrNull(): WebArrangement? = when (this) {
    is WebColumn -> arrangement
    is WebRow -> arrangement
    else -> null
}

private fun WebComponent.withArrangement(a: WebArrangement): WebComponent = when (this) {
    is WebColumn -> copy(arrangement = a)
    is WebRow -> copy(arrangement = a)
    else -> this
}

private fun parseColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val c = hex.removePrefix("#").trim()
    val rgb = when (c.length) {
        3 -> c.map { "$it$it" }.joinToString("")
        6 -> c
        8 -> c.substring(2)
        else -> return null
    }
    return runCatching { Color(("FF$rgb").toLong(16)) }.getOrNull()
}

private fun Color.toHex6(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

/** Returns a copy of [this] with its [WebModifier] replaced (per concrete type). */
private fun WebComponent.withModifier(mod: WebModifier): WebComponent = when (this) {
    is WebColumn -> copy(modifier = mod)
    is WebRow -> copy(modifier = mod)
    is WebBox -> copy(modifier = mod)
    is WebText -> copy(modifier = mod)
    is WebButton -> copy(modifier = mod)
    is WebImage -> copy(modifier = mod)
    is WebLink -> copy(modifier = mod)
    is WebInput -> copy(modifier = mod)
    is WebSpacer -> copy(modifier = mod)
}
