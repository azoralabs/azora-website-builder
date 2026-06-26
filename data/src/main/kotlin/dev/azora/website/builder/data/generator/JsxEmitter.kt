package dev.azora.website.builder.data.generator

import dev.azora.sdk.core.project.domain.CodeGenerator.GenScope
import dev.azora.website.builder.domain.*

/**
 * Renders a [WebComponent] tree as JSX into a [GenScope]. Each node gets `className` matching the
 * rule emitted by [CssEmitter]. A node whose id is in `instances` is a live component instance
 * (`instances[id]` is a component **name**) and is rendered as `<ReactName />` (resolved via
 * [reactNames]); the referenced component is imported by the caller. Text content is emitted as a JS
 * string expression so it never needs JSX escaping.
 */
object JsxEmitter {

    fun emit(
        scope: GenScope,
        node: WebComponent,
        instances: Map<String, String>,
        reactNames: Map<String, String>
    ) {
        // Component instance → render the referenced component as a child element.
        instances[node.id]?.let { componentName ->
            reactNames[componentName]?.let { name -> scope.write("<$name />") }
            return
        }

        val cls = CssEmitter.cssClass(node.id)
        when (node) {
            is WebColumn -> container(scope, cls, node.children, instances, reactNames)
            is WebRow -> container(scope, cls, node.children, instances, reactNames)
            is WebBox -> container(scope, cls, node.children, instances, reactNames)
            is WebText -> scope.write("<span className=\"$cls\">{${js(node.text)}}</span>")
            // Our custom Button component (AzButton), not a native browser <button>.
            is WebButton -> scope.write("<AzButton className=\"$cls\">{${js(node.label)}}</AzButton>")
            is WebLink -> scope.write("<a className=\"$cls\" href={${js(node.href)}}>{${js(node.text)}}</a>")
            is WebImage -> scope.write("<img className=\"$cls\" src={${js(node.src)}} alt={${js(node.alt)}} />")
            is WebInput -> scope.write("<input className=\"$cls\" placeholder={${js(node.placeholder)}} />")
            is WebSpacer -> scope.write("<div className=\"$cls\" />")
        }
    }

    private fun container(
        scope: GenScope,
        cls: String,
        children: List<WebComponent>,
        instances: Map<String, String>,
        reactNames: Map<String, String>
    ) {
        if (children.isEmpty()) {
            scope.write("<div className=\"$cls\" />")
            return
        }
        scope.write("<div className=\"$cls\">")
        scope.gen { children.forEach { emit(this, it, instances, reactNames) } }
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
