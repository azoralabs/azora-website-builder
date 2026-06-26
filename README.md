# Azora Website Builder

A plugin for **Azora Studio** that builds websites visually and generates a
[React 19.2](https://react.dev) app. Pages and reusable components are individual files you create
and edit in a node-graph editor; **Generate** emits a runnable [Vite](https://vite.dev) + React
project (JSX + CSS).

- **Plugin id:** `dev.azora.website_builder`
- **Version:** `0.0.1`
- **Category:** Generator

## Project layout

A Website project on disk:

```
<project>/
  project.azora      # project metadata (host-owned)
  pages/             # one editable page per file  (<Name>.azscene)
  components/        # one editable reusable component per file  (<Name>.azscene)
  generated/         # the emitted React 19.2 (Vite, JSX + CSS) app
```

`pages/` and `components/` hold the **Azora visual model** as `.azscene` files (JSON content) — the
source of truth you edit. `generated/` is build output, regenerated from them. No Gradle/Kotlin
files are produced — only the React project.

## Workflow

A single workspace (no tabs): a file explorer on the left, the node editor on the right.

- **Right-click** the *Pages* or *Components* section (or press `+`) to create one; click a file to
  open and edit it; `✎`/`✕` rename/delete. Edits save to that file with **Save**.
- The node editor: every element is a node, containers expose a `children` port (drag to re-parent),
  right-click the canvas to add elements or insert a **component instance**.
- **Component instances** are live references: inserting a component places a single node; editing
  that component updates everywhere it's used. Instances become real React child components.
- **Generate** emits/refreshes the React app in `generated/`.

## Generated React app

`generated/` is a standard Vite + React 19.2 project:

```
generated/ package.json vite.config.js index.html
  src/ main.jsx App.jsx index.css
  src/pages/<Name>.jsx + .css        # routed via react-router-dom
  src/components/<Name>.jsx + .css    # imported where instanced
```

- Each page/component → a `.jsx` (default-exported function) importing its `.css`.
- `WebModifier` styling → CSS rules (flexbox for containers); routes come from each page's `route`.
- Run it: `cd generated && npm install && npm run dev` (or `npm run build`).

## Architecture

```
src/main/kotlin/dev/azora/website/builder/
├── WebsiteBuilderPlugin.kt        # AzoraPlugin: template, single Content workspace, run targets
├── editor/                        # Compose UI
│   ├── WebsiteWorkspace.kt        # file explorer + per-file node editor
│   ├── RootTreeEditor / ComponentTreeCanvas / ComponentPropertiesPanel / WebComponentTree
│   └── node/WebNodeCanvas.kt      # adapter over the Azora SDK node-graph canvas
├── generator/react/               # React 19.2 emitter (ReactSiteGenerator, JsxEmitter, CssEmitter, ReactProjectFiles)
└── model/ProjectFiles.kt          # PageFile/ComponentFile + on-disk read/write over FileSystem
```

**Reuses the Azora SDK node canvas** (`dev.azora.canvas`): `AzoraEditorCanvas` + `AzoraNode` driven
by `AzoraCanvasStateHolder`, via the thin `WebNodeCanvas` adapter — the builder doesn't reimplement a
node graph.

**One-way dependency on the SDK.** Studio has no compile dependency on this plugin — it's discovered
at runtime from the JAR's `plugin.json`. The plugin compiles `compileOnly` against the SDK (Studio
provides those classes at runtime) and resolves SDK modules through a Gradle composite build of a
sibling `../azora-studio` checkout (`settings.gradle.kts`); `azora-studio` is never modified.

**Persistence is file-based.** Pages/components live as `.azscene` files (JSON) under
`pages/`/`components/` (via `FileSystem` from `PluginContext`); `project.azora` holds only host-owned
project metadata.

## Building

Requirements: JDK 17+, a sibling `azora-studio` checkout at `../azora-studio`, and Node.js + npm
(to run the generated site).

```bash
./gradlew jar          # -> build/libs/dev.azora.website_builder-0.0.1.jar
./gradlew compileKotlin # compile only
```

Drop the JAR into Studio's plugins directory and (re)load it.

## Tech

Kotlin 2.4.0 · Jetbrains Compose · kotlinx-serialization · JVM toolchain 17 · Azora SDK
(`azora-sdk-core`, `azora-sdk-plugin`, `azora-sdk` canvas) · React 19.2 + Vite (generated output).

## Notes / limitations

- Theme, Navigation, API and feature-flag editing were removed in the move to file-based pages +
  components; routing is derived from each page's `route`.
- Page event/logic handlers are not emitted yet (buttons render without actions).
- No Gradle/Kotlin output and no Studio run targets — run the generated app directly with npm.
