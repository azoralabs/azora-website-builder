package dev.azora.website.builder.data.generator

/** A page's routing info used to build the router in `App.jsx`. */
data class RoutePage(val componentName: String, val route: String, val isHome: Boolean)

/**
 * Static files for the generated Vite + React 19.2 app under `generated/`. Every file is produced
 * through the SDK [dev.azora.sdk.core.project.domain.CodeGenerator] (via [buildSource]) so indentation
 * is consistent and no hand-built strings are used.
 */
object ReactProjectFiles {

    fun packageJson(appName: String): String = buildSource {
        write("{")
        gen {
            write("\"name\": \"$appName\",")
            write("\"private\": true,")
            write("\"version\": \"0.0.0\",")
            write("\"type\": \"module\",")
            write("\"scripts\": {")
            gen {
                write("\"dev\": \"vite\",")
                write("\"build\": \"vite build\",")
                write("\"preview\": \"vite preview\"")
            }
            write("},")
            write("\"dependencies\": {")
            gen {
                write("\"react\": \"^19.2.0\",")
                write("\"react-dom\": \"^19.2.0\",")
                write("\"react-router-dom\": \"^7.9.0\"")
            }
            write("},")
            write("\"devDependencies\": {")
            gen {
                write("\"@vitejs/plugin-react\": \"^4.3.4\",")
                write("\"vite\": \"^6.0.0\"")
            }
            write("}")
        }
        write("}")
    }

    val viteConfig: String = buildSource {
        write("import { defineConfig } from 'vite'")
        write("import react from '@vitejs/plugin-react'")
        blank()
        write("export default defineConfig({")
        gen { write("plugins: [react()],") }
        write("})")
    }

    fun indexHtml(title: String): String = buildSource {
        write("<!doctype html>")
        write("<html lang=\"en\">")
        gen {
            write("<head>")
            gen {
                write("<meta charset=\"UTF-8\" />")
                write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />")
                write("<title>${title.ifBlank { "Azora Site" }}</title>")
            }
            write("</head>")
            write("<body>")
            gen {
                write("<div id=\"root\"></div>")
                write("<script type=\"module\" src=\"/src/main.jsx\"></script>")
            }
            write("</body>")
        }
        write("</html>")
    }

    val mainJsx: String = buildSource {
        write("import React from 'react'")
        write("import { createRoot } from 'react-dom/client'")
        write("import { BrowserRouter } from 'react-router-dom'")
        write("import App from './App.jsx'")
        write("import './index.css'")
        blank()
        write("createRoot(document.getElementById('root')).render(")
        gen {
            write("<React.StrictMode>")
            gen {
                write("<BrowserRouter>")
                gen { write("<App />") }
                write("</BrowserRouter>")
            }
            write("</React.StrictMode>,")
        }
        write(")")
    }

    val indexCss: String = buildSource {
        write(":root { color-scheme: light dark; }")
        write("* { box-sizing: border-box; }")
        write("html, body, #root { margin: 0; height: 100%; }")
        write("body { font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }")
        blank()
        // Our button (matches the Azora "primary" button): brand fill, white label, soft shadow.
        write(".az-button {")
        write("  display: inline-flex; align-items: center; justify-content: center; gap: 8px;")
        write("  height: 36px; padding: 0 24px; border: none; border-radius: 8px;")
        write("  background: #D14EEA; color: #FFFFFF;")
        write("  font-family: inherit; font-size: 11px; font-weight: 500; line-height: 1;")
        write("  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2); cursor: pointer;")
        write("}")
    }

    /** Reusable custom button component used wherever a button node is emitted (never a native
     *  `<button>`). Merges the base `.az-button` look with any per-node class the page passes. */
    val azButtonJsx: String = buildSource {
        write("export default function AzButton({ className, children, ...props }) {")
        gen {
            write("const cls = ['az-button', className].filter(Boolean).join(' ')")
            write("return (")
            gen { write("<button className={cls} {...props}>{children}</button>") }
            write(")")
        }
        write("}")
    }

    /** `App.jsx` wiring react-router routes from the project's pages. */
    fun appJsx(pages: List<RoutePage>): String = buildSource {
        write("import { Routes, Route } from 'react-router-dom'")
        pages.forEach { write("import ${it.componentName} from './pages/${it.componentName}.jsx'") }
        blank()
        write("export default function App() {")
        gen {
            write("return (")
            gen {
                write("<Routes>")
                gen { pages.forEach { write("<Route path=\"${it.route}\" element={<${it.componentName} />} />") } }
                write("</Routes>")
            }
            write(")")
        }
        write("}")
    }
}
