package dev.azora.website.builder.data.generator

import dev.azora.sdk.core.project.domain.CodeGenerator.GenScope
import dev.azora.sdk.compiler.scene.domain.*
import dev.azora.website.builder.domain.*

/**
 * Renders a [SceneComponent] graph as JSX into a [GenScope]. Containers resolve their ordered [SceneSlot]s
 * against [pool]; a node referenced by several slots renders once per reference (reuse). `visiting`
 * is a path guard: a node in the current ancestor chain is skipped, so cycles (a slot referencing an
 * ancestor) can't infinite-loop while DAG reuse still renders duplicates.
 *
 * Each node gets `className` matching the rule emitted by [CssEmitter]. A node whose id is in
 * `instances` is a live component instance (`instances[id]` is a component **name**) rendered as
 * `<ReactName />` (resolved via [reactNames]); the referenced component is imported by the caller.
 * Text content is emitted as a JS string expression so it never needs JSX escaping.
 */
object JsxEmitter {

    fun emit(
        scope: GenScope,
        node: SceneComponent,
        pool: Map<String, SceneComponent>,
        instances: Map<String, String>,
        reactNames: Map<String, String>,
        visiting: Set<String> = emptySet()
    ) {
        if (node.id in visiting) return // cycle: a slot referenced an ancestor
        // Component instance → render the referenced component as a child element.
        instances[node.id]?.let { componentName ->
            reactNames[componentName]?.let { name -> scope.write("<$name />") }
            return
        }

        val cls = CssEmitter.cssClass(node.id)
        when (node) {
            is SceneColumn -> container(scope, cls, node.slots, pool, instances, reactNames, visiting + node.id)
            is SceneRow -> container(scope, cls, node.slots, pool, instances, reactNames, visiting + node.id)
            is SceneBox -> container(scope, cls, node.slots, pool, instances, reactNames, visiting + node.id)
            is SceneText -> scope.write("<span className=\"$cls\">{${js(node.text)}}</span>")
            // Our custom Button component (AzButton), not a native browser <button>.
            is SceneButton -> scope.write("<AzButton className=\"$cls\">{${js(node.label)}}</AzButton>")
            is SceneLink -> scope.write("<a className=\"$cls\" href={${js(node.href)}}>{${js(node.text)}}</a>")
            is SceneImage -> scope.write("<img className=\"$cls\" src={${js(node.src)}} alt={${js(node.alt)}} />")
            is SceneInput -> scope.write("<input className=\"$cls\" placeholder={${js(node.placeholder)}} />")
            is SceneSpacer -> scope.write("<div className=\"$cls\" />")
        }
    }

    private fun container(
        scope: GenScope,
        cls: String,
        slots: List<SceneSlot>,
        pool: Map<String, SceneComponent>,
        instances: Map<String, String>,
        reactNames: Map<String, String>,
        visiting: Set<String>
    ) {
        // Resolve each occupied slot in order — render per reference so a reused node appears N times.
        val children = slots.mapNotNull { s -> s.childId?.let { pool[it] } }
        if (children.isEmpty()) {
            scope.write("<div className=\"$cls\" />")
            return
        }
        scope.write("<div className=\"$cls\">")
        scope.gen { children.forEach { emit(this, it, pool, instances, reactNames, visiting) } }
        scope.write("</div>")
    }

    /** A JS string literal for [s], safe to embed inside a JSX `{ ... }` expression. */
    private fun js(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "\"$escaped\""
    }
}
