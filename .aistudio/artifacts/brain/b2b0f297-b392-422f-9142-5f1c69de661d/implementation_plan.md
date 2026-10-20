# OpenMuse Mobile — Local Android Agent

OpenMuse Mobile brings the OpenMuse autonomous personal AI agent architecture to Android as a self-contained, local-first application capable of running on an 8 GB RAM smartphone without a desktop PC or mandatory remote VPS. It combines a Gemini-powered multi-step task engine, an Android-native PRoot/Linux userspace manager, a local loopback backend & task worker, a two-layer browser (interactive WebView + autonomous DOM/JS inspection bridge with PRoot Chromium diagnostics), workspace file & Git operations, and local SQLite persistence.

### User Review & Critical Decisions

> [!IMPORTANT]
> Because the clarifying questions were skipped, the plan adopts the recommended engineering defaults below. Please review and click **Proceed** (or suggest adjustments) to begin implementation across all phases.

- **Confirmed Default 1 — Native Android Architecture with OpenMuse Parity**: Since the workspace is initialized with a native Android (Kotlin / Jetpack Compose) build toolchain rather than an imported React Native/Expo tree, we will implement the full OpenMuse Mobile experience natively in Kotlin & Jetpack Compose while preserving all OpenMuse screens (Chat with inline AG-UI cards, Tasks/Activity, Two-Layer Browser, Workspace Files & Git, Interactive Terminal, Linux Environment Manager, and Settings/Diagnostics) plus a local loopback HTTP health/task server bound to `127.0.0.1`.
- **Confirmed Default 2 — PRoot Runtime & Resumable Userspace Manager**: The runtime manager targets ARM64 (`arm64-v8a`) first, supports resumable/cancellable rootfs archive downloads with SHA-256 verification and free-storage preflight checks into app-private storage, executes real subprocesses via `AndroidProotComputerProvider` (using PRoot when installed and a verified local Android POSIX shell execution context for immediate PoC and fallback), and surfaces Android SELinux/W^X (`seccomp` / `execve` from app-private directories on Android 10+) constraints transparently in the UI.
- **Confirmed Default 3 — Two-Layer Browser Automation**: Layer A provides a full interactive Android WebView browser (tabs, URL bar, navigation controls, history, bookmarks, safe downloads/SAF export, and JS toggle). Layer B provides an autonomous browser controller backed by a real WebView DOM/JavaScript evaluation & screenshot capture bridge for reliable on-device automation, paired with an explicit PRoot ARM64 Chromium/Playwright compatibility probe that records library, sandbox, and kernel findings without faking Playwright runs.
- **Confirmed Default 4 — Standalone Local SQLite Persistence**: All conversations (main + side threads, rename, archive, restore), projects, task checkpoints, execution receipts, and terminal logs are persisted locally on-device using Room SQLite so the app works immediately without requiring a CopilotKit Intelligence key, while retaining optional remote thread sync settings.

---

### 1. Overview & Core Concept

- **What It Does**: OpenMuse Mobile is an autonomous personal AI agent and local developer workstation for Android. Users can assign multi-step tasks in natural language, inspect live execution plans, approve sensitive checkpoints, run real shell and Git commands in an isolated workspace, browse and automate web pages with human-in-the-loop takeover, and manage local files.
- **Target Audience / Persona**: Developers, researchers, and technical power users who want an inspectable, local-first autonomous agent on their phone that executes real tools and reports verifiable receipts rather than simulated responses.
- **Key Value**: Eliminates mandatory Docker desktop dependencies and cloud agent hosting by pairing Gemini tool-calling (`gemini-3.1-pro-preview` and `gemini-flash-latest`) with a device-local execution engine, loopback-authenticated task worker, and structured SQLite state persistence.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Zero-State & Environment Health Check**: Launching the app opens the OpenMuse command center with a live status bar indicating the health of the Local Loopback API (`127.0.0.1`), Task Worker, SQLite Database, Gemini API Configuration, and Linux/PRoot Runtime.
  2. **Streamed AG-UI Chat & Inline Receipts**: Users converse in main or side threads. When the agent plans a task or invokes a tool (`run_command`, `read_file`, `write_file`, `git_operation`, `browse_url`, `extract_dom`), inline generative UI cards display the step plan, live stdout/stderr, file diffs, or browser snapshots. Consequential actions pause at an explicit **Human Approval Checkpoint** card (`Approve` / `Reject`).
  3. **Autonomous Task Engine & Activity Monitor**: The Activity screen lists active, paused, completed, and failed tasks with step-by-step checkpoints, bounded retry counters, elapsed timers, and controls to **Pause**, **Resume**, **Retry**, or **Cancel** execution.
  4. **Two-Layer Browser & Human Takeover**: Users switch seamlessly between interactive multi-tab WebView browsing and autonomous agent inspection. A prominent **Take Control / Hand Back to Agent** toggle allows the user to complete logins or resolve prompts before resuming automation. All DOM text extracted from web pages is tagged as untrusted input.
  5. **Workspace Files, Git & Terminal**: Users browse their app-controlled workspace, edit code/text files, import/export external files via Android Storage Access Framework (SAF), run Git operations (`status`, `diff`, `branch`, `log`, `commit`, `clone`), and execute commands in an interactive terminal session with saved execution receipts.
  6. **Linux Environment & Runtime Manager**: A dedicated screen displays rootfs installation status, free vs. used disk space, SHA-256 verification status, resumable download controls, PRoot binary & SELinux/seccomp probe diagnostics, package setup logs, and **Install / Start / Stop / Restart / Repair / Uninstall** actions.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Precision Autonomous Studio** — an editorial dark-first technical workspace inspired by OpenMuse and high-craft developer instruments. Crisp 1dp structural borders, high-contrast status telemetry badges, and zero decorative clutter.
  - *Color Palette & Mood*:
    - `--background`: Deep Obsidian (`#0B0D11`) for primary canvas; Warm Alabaster (`#F7F8FA`) in light mode.
    - `--surface`: Elevated Slate (`#131720`) for cards and panes; Subtle Graphite (`#1C2230`) for interactive tool receipts and terminal blocks.
    - `--primary`: Electric Cyan-Teal (`#22D3EE`) for active agent intelligence, streaming indicators, and primary CTAs.
    - `--secondary`: Crisp Indigo (`#818CF8`) for side-conversation threads, browser automation badges, and plan checkpoints.
    - `--accent` / Status Tokens: Emerald (`#10B981`) for verified exit code `0` / healthy services; Amber (`#F59E0B`) for human approval gates and PRoot sandbox warnings; Rose (`#F43F5E`) for non-zero exit codes and destructive command confirmations.
  - *Typography & Hierarchy*:
    - **Display & Section Headers**: `Space Grotesk` (bundled locally via `font-util`) for sharp, technical headlines and screen titles.
    - **Body & Conversation Copy**: `Plus Jakarta Sans` (bundled locally) for clean legibility across long agent responses and plans.
    - **Terminal, Logs, Receipts & Paths**: `JetBrains Mono` (bundled locally) for shell output, exit codes, SHA-256 hashes, file diffs, and JSON tool arguments.
  - *Component Styling & Layout*: Material 3 adaptive layout supporting both compact phones (bottom `NavigationBar`) and expanded foldables/tablets (`NavigationRail` + side-by-side list-detail panes), full edge-to-edge safe drawing insets, and minimum `48dp` interactive touch targets.
- **Interactive Feedback & Motion**:
  - Live pulsing telemetry pills when the local task worker or terminal subprocess is executing.
  - Expandable execution receipt drawers with copyable stdout/stderr and execution duration (`ms`).
  - Destructive action confirmation modals showing the exact command array, working directory, and security boundary warning before execution.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Android Agent Runtime + Loopback Server over Docker**
  - *Chosen Approach*: Replace OpenMuse's desktop Docker computer provider with `AndroidProotComputerProvider` and a local loopback HTTP service (`127.0.0.1` with per-session HMAC/Bearer token authentication).
  - *Why*: Android phones cannot run the Linux Docker daemon without root/virtualization. Running an in-process and loopback-bound worker with real `ProcessBuilder` / PRoot execution works within 8 GB RAM and strictly prevents public network exposure of the terminal API.
  - *Alternatives Considered*: Requiring a remote VPS or Termux installation was rejected to keep the app self-contained and honest about platform boundaries.

- **Decision 2: PRoot Binary Packaging, Resumable Rootfs & Android W^X Transparency**
  - *Chosen Approach*: Provide a full lifecycle manager (`Install`, `Start`, `Stop`, `Restart`, `Repair`, `Uninstall`) that checks free disk space, performs resumable/cancellable HTTPS downloads of an ARM64 Linux minimal userspace with SHA-256 verification into app-private storage, runs a real shell PoC verification test, and executes workspace commands without nested PRoot invocations.
  - *Why*: Bundling a multi-gigabyte rootfs inside the APK bloats install size, while Android 10+ SELinux W^X policies restrict executing downloaded binaries from writable app data unless packaged in native library directories (`jniLibs`) or executed via supported loader paths. Our runtime probes and reports the exact execution mode (PRoot userspace vs. direct Android POSIX shell) and never fabricates command output.

- **Decision 3: Two-Layer Browser with Prompt-Injection Isolation**
  - *Chosen Approach*: Combine a multi-tab interactive Android `WebView` with an autonomous DOM/JS automation bridge that captures real page titles, cleaned DOM trees, interactive selectors, and screenshots, plus an explicit PRoot Chromium/Playwright diagnostic probe.
  - *Why*: Full desktop Chromium inside PRoot on Android frequently hits `clone`/`seccomp` sandbox and shared-memory (`/dev/shm`) failures. By separating interactive browsing, verified WebView DOM automation, and Chromium PRoot diagnostics—and wrapping all extracted webpage text in untrusted-content delimiters that cannot auto-approve shell commands—we guarantee reliable local browser automation without simulating Playwright results.

- **Decision 4: Gemini Direct REST Provider with Secret Isolation**
  - *Chosen Approach*: Integrate Gemini (`gemini-3.1-pro-preview` default for complex reasoning/planning, `gemini-flash-latest` for fast tool loops, plus user-configurable model selection) via Retrofit + Kotlinx Serialization with SSE streaming, structured JSON plans, and real tool declarations, reading `BuildConfig.GEMINI_API_KEY` (and optional encrypted user-supplied override stored in Android Keystore-backed preferences, redacted from all terminal environments and logs).
  - *Why*: Ensures zero hardcoded keys, strips API credentials from subprocess `ProcessBuilder.environment()` maps so shell commands cannot leak secrets, and provides clear rate-limit, timeout, and retry handling.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     OPENMUSE MOBILE — ANDROID UI LAYER                       │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │  Chat & AG-UI│ │ Tasks & Plan │ │  Two-Layer   │ │ Workspace, Terminal  │ │
│  │ Inline Cards │ │  Checkpoints │ │   Browser    │ │ & Linux Env Manager  │ │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘ │
└─────────┼────────────────┼────────────────┼────────────────────┼─────────────┘
          │                │                │                    │
          ▼                │                ▼                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                  VIEWMODEL & STATE ORCHESTRATION LAYER                       │
│         OpenMuseViewModel (StateFlow, Approval Gates, Health Telemetry)      │
└─────────┬────────────────────────┬─────────────────────────┬─────────────────┘
          │                        │                         │
          ▼                        ▼                         ▼
┌─────────────────────┐  ┌────────────────────────┐  ┌─────────────────────────┐
│ LOCAL LOOPBACK API  │  │ AUTONOMOUS TASK ENGINE │  │  BROWSER AUTOMATION &   │
│ 127.0.0.1 + Bearer  │  │  Plan -> Tool -> Obs   │  │  DOM EXTRACTION BRIDGE  │
│  /health, /tasks,   │◄─┤  Checkpoints, Retries, │─►│  WebView Controller +   │
│  /terminal, /files  │  │  Approval Gate, Budget │  │  Untrusted DOM Sanitizer│
└─────────┬───────────┘  └───────────┬────────────┘  └─────────────────────────┘
          │                          │
          ▼                          ▼
┌───────────────────────────────────────────┐  ┌───────────────────────────────┐
│   ANDROID PROOT COMPUTER PROVIDER         │  │ GEMINI AI MODEL PROVIDER      │
│  - PRoot Runtime Lifecycle & SHA256 Check │  │ - Retrofit REST + SSE Stream  │
│  - Validated Arg Execution (No unsafe sh) │  │ - Function Calling & Schemas  │
│  - Workspace File I/O, SAF & Git CLI      │  │ - Env Secret Redaction Guard  │
└─────────────────────┬─────────────────────┘  └───────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                LOCAL SQLITE PERSISTENCE (ROOM DATABASE)                      │
│  Conversations (Main/Side) │ Messages & Receipts │ Tasks & Steps │ Browser   │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Data Model & State (Room SQLite Entities)

1. **`ProjectWorkspaceEntity`**: `id`, `name`, `rootPath`, `gitBranch`, `createdAt`, `lastOpenedAt`.
2. **`ConversationThreadEntity`**: `id`, `projectId`, `title`, `isSideThread`, `parentThreadId`, `isArchived`, `externalCopilotThreadId` (nullable for optional external sync), `updatedAt`.
3. **`ChatMessageEntity`**: `id`, `threadId`, `role` (`USER`, `ASSISTANT`, `TOOL`, `SYSTEM`), `content`, `cardType` (`TEXT`, `PLAN`, `COMMAND_RECEIPT`, `FILE_DIFF`, `BROWSER_SNAPSHOT`, `APPROVAL_REQUEST`), `metadataJson`, `createdAt`.
4. **`AutonomousTaskEntity`**: `id`, `threadId`, `goal`, `status` (`PLANNING`, `RUNNING`, `AWAITING_APPROVAL`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`), `currentStepIndex`, `maxStepsBudget`, `retryCount`, `maxRetries`, `timeoutMs`, `errorMessage`, `createdAt`, `updatedAt`.
5. **`TaskStepEntity`**: `id`, `taskId`, `stepIndex`, `title`, `toolName`, `toolArgsJson`, `requiresApproval`, `status`, `observationOutput`, `exitCode`, `durationMs`, `timestamp`.
6. **`CommandReceiptEntity`**: `id`, `workspaceId`, `commandJson`, `workingDir`, `stdout`, `stderr`, `exitCode`, `durationMs`, `executionMode` (`PROOT_USERSPACE` or `ANDROID_POSIX_SHELL`), `timestamp`.
7. **`BrowserTabEntity` & `BookmarkEntity`**: Persisted tab state (`id`, `title`, `url`, `isAgentControlled`, `lastSnapshotPath`) and saved bookmarks/history.

#### Interactive Component & State Mapping

- **Chat & AG-UI Stream**: Submitting a prompt creates a `ChatMessageEntity` and triggers either a direct Gemini tool-augmented turn or an `AutonomousTaskEntity`. Tool calls requiring destructive shell access, package installation, or remote Git push transition the task to `AWAITING_APPROVAL` and render an interactive approval card.
- **Autonomous Task Loop (`AutonomousTaskEngine`)**: Runs inside a managed coroutine scope (with Android foreground service notification support for active long-running tasks). Executes `Understand -> Plan -> Select Approved Tool -> Execute -> Collect Real Observation -> Checkpoint to Room -> Next Step or Complete`. Enforces per-task timeout, bounded retries with backoff, and immediate cancellation token propagation to running subprocesses.
- **`AndroidProotComputerProvider`**:
  - Validates working directory boundaries inside `filesDir/openmuse_workspace`.
  - Executes commands using structured argument lists (`ProcessBuilder(commandList)`) with sanitized environment variables (explicitly stripping `GEMINI_API_KEY` and loop auth tokens).
  - Supports file creation, reading, line-range editing, SAF import/export, and Git commands (`init`, `status`, `diff`, `branch`, `log`, `commit`, `clone`).
- **Linux Environment Manager (`ProotRuntimeManager`)**:
  - Performs preflight storage checks (`StatFs`), resumable HTTP range downloads of the ARM64 rootfs tarball with live progress flow, SHA-256 digest verification, safe tar/gzip extraction with symlink traversal guards, and a startup PoC probe (`uname -a`, `id`, exit code verification).
- **CI / GitHub Actions Workflow & Verification Suite**:
  - `.github/workflows/android-apk.yml` configures JDK 17, Android SDK, Gradle caching, unit/Robolectric tests, and builds `app-debug.apk`, publishing it as a downloadable workflow artifact.
  - Local JVM & Robolectric integration tests verify PRoot runtime state transitions, Linux shell execution & receipt recording, workspace file creation, Room task persistence, loopback API bearer authentication, timeout/cancellation, Gemini error handling, and APK compilation.
