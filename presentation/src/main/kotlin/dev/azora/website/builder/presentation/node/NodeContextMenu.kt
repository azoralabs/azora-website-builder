package dev.azora.website.builder.presentation.node

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azora.website.builder.presentation.theme.orOnSurface
import kotlin.math.roundToInt

data class NodeMenuItem(val label: String, val color: Color = Color.Unspecified, val onClick: () -> Unit)

data class NodeMenuSection(val header: String?, val items: List<NodeMenuItem>)

@Composable
fun NodeContextMenu(position: Offset, sections: List<NodeMenuSection>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(IntrinsicSize.Max)
            .shadow(10.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainer)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.width(IntrinsicSize.Max).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            sections.forEachIndexed { sectionIndex, section ->
                if (sectionIndex > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
                section.header?.let { header ->
                    Text(header, color = cs.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
                }
                section.items.forEachIndexed { index, item ->
                    Box(Modifier.fillMaxWidth().clickable { item.onClick(); onDismiss() }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(item.label, color = item.color.orOnSurface(), fontSize = 12.sp)
                    }
                    if (index < section.items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

@Composable
fun NodeContextMenu(position: Offset, items: List<NodeMenuItem>, onDismiss: () -> Unit, header: String? = null) {
    NodeContextMenu(position, listOf(NodeMenuSection(header, items)), onDismiss)
}
