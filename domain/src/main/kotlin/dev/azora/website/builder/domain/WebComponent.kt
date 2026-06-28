package dev.azora.website.builder.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Visual styling applied to a [WebComponent]. Plugin-owned (azora-studio knows nothing about it).
 *
 * Values are primitive and framework-agnostic so the same model drives both the React generator and
 * the in-Studio preview. Lengths are CSS pixels, colors are `#RRGGBB` hex strings; `null`/default
 * fields are simply not emitted.
 */
@Serializable
data class WebModifier(
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val padding: Int? = null,
    val gap: Int? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val fontSize: Int? = null,
    val fontWeight: WebFontWeight = WebFontWeight.NORMAL,
    val textAlign: WebTextAlign = WebTextAlign.START,
    /** Border radius in CSS px (`border-radius`). */
    val cornerRadius: Int? = null,
    /** Border width in CSS px (`border` with [borderColor]); null = no border. */
    val borderWidth: Int? = null,
    /** Border color as `#RRGGBB` (paired with [borderWidth]). */
    val borderColor: String? = null,
    /** Opacity as a percentage 0–100 (`opacity`); null = fully opaque. */
    val opacity: Int? = null
)

@Serializable
enum class WebFontWeight { NORMAL, MEDIUM, SEMI_BOLD, BOLD }

@Serializable
enum class WebTextAlign { START, CENTER, END }

@Serializable
enum class WebArrangement { START, CENTER, END, SPACE_BETWEEN }

/**
 * A node in a page's visual component tree. Nodes live in a flat per-scene pool
 * ([WebSceneDoc.nodes]); containers reference their children by id through ordered [WebSlot]s, so the
 * same node can be reused in multiple slots. The React generator and the in-Studio preview both walk
 * from [WebSceneDoc.rootId], resolving slots against the pool.
 */
@Serializable
sealed interface WebComponent {
    val id: String
    val modifier: WebModifier
}

/**
 * One ordered out-slot on a container (Unreal array-pin style). [childId] references a node in the
 * scene pool, or `null` for an empty slot added via `+`. Multiple slots (across containers) may
 * reference the same [childId] — the node renders once per reference.
 */
@Serializable
data class WebSlot(
    val id: String = randomSlotId(),
    val childId: String? = null
)

@Serializable
@SerialName("column")
data class WebColumn(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val arrangement: WebArrangement = WebArrangement.START,
    val slots: List<WebSlot> = emptyList()
) : WebComponent

@Serializable
@SerialName("row")
data class WebRow(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val arrangement: WebArrangement = WebArrangement.START,
    val slots: List<WebSlot> = emptyList()
) : WebComponent

@Serializable
@SerialName("box")
data class WebBox(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val slots: List<WebSlot> = emptyList()
) : WebComponent

@Serializable
@SerialName("text")
data class WebText(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val text: String = "Text"
) : WebComponent

@Serializable
@SerialName("button")
data class WebButton(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val label: String = "Button",
    /** Id of a click handler to run, if any (logic emission is not implemented yet). */
    val onClickHandlerId: String? = null
) : WebComponent

@Serializable
@SerialName("image")
data class WebImage(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val src: String = "",
    val alt: String = ""
) : WebComponent

@Serializable
@SerialName("link")
data class WebLink(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val text: String = "Link",
    val href: String = "/"
) : WebComponent

@Serializable
@SerialName("input")
data class WebInput(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier(),
    val placeholder: String = "",
    /** Optional state key bound to this input. */
    val stateKey: String? = null
) : WebComponent

@Serializable
@SerialName("spacer")
data class WebSpacer(
    override val id: String = randomComponentId(),
    override val modifier: WebModifier = WebModifier()
) : WebComponent

/** Generates a short, collision-resistant id for a component. */
fun randomComponentId(): String = "c_" + kotlin.random.Random.nextLong().toString(36)

/** Generates a short id for an empty out-slot on a container (distinct from component ids). */
fun randomSlotId(): String = "s_" + kotlin.random.Random.nextLong().toString(36)
