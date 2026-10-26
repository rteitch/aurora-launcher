# Architecture Rationale (`architect` Synthesis)

## Context and Constraints

Aurora Launcher ([prd.md](file:///d:/Project/launcher-ios/prd.md) sections 7, 8, 9, and 10) enforces two hard structural rules:
1. Feature modules depend only on `:core:*` modules (`:feature:* -> :core:*`, never `:feature:* -> :feature:*`).
2. Home Workspace embeds `AppLibrary` as its rightmost horizontal pager page, hosts live `AppWidgetHostView` instances inside grid cells, queues `LauncherApps.Callback` package mutations during active drag gestures (E5), and gates locked apps behind `BiometricPrompt` (UC-12, E29) from every launch entry point.

## Candidates Evaluated

### Candidate A: Coordinator-Centric Shell
- **Shape**: Stateless `:feature:*` composables emit events upward to a single `LauncherCoordinatorViewModel` in `:app` that owns all state slices, overlay transitions, and E5 drag-time package queueing.
- **Strengths**: Single state machine guarantees overlay mutual exclusion, unified biometric launch gating, and simple ViewModel-free Compose UI testing.
- **Weaknesses**: Concentrates six features into one wide ViewModel in `:app`, leaking transient local state (Spotlight search query, Settings forms, App Library category filter) into `:app` and risking whole-shell recomposition churn.

### Candidate B: Autonomous Feature ViewModels + Session Bus
- **Shape**: Every `:feature:*` module owns an internal `@HiltViewModel` backed by reactive `:core:data` repositories, coordinating overlays and drag locks through a shared `LauncherSessionRepository` in `:core:data`.
- **Strengths**: Isolates recomposition scopes (typing in Spotlight never recomposes the Home grid) and keeps `:app` thin.
- **Weaknesses**: Placing `HomeOverlayRoute` and pixel bounds inside `:core:data` violates boundary discipline by leaking presentation state into the persistence layer, and unclosed drag flags across ViewModels can stall package sync without explicit lease scoping.

## Synthesis Decision

We adopt Candidate B's **per-feature `@HiltViewModel` + stateless `*Screen` pair** grafted onto Candidate A's **slot composition and centralized `:app` shell overlay + biometric launch gate**, with a **lease-scoped `PackageSyncCoordinator`** in `:core:data`:

1. **Zero `:feature -> :feature` imports via Composable slots**: `HomeRoute` accepts `appLibrarySlot: @Composable () -> Unit` for its rightmost pager page and `widgetSlot: @Composable (WorkspaceItem.Widget) -> Unit` (plus `widgetStackSlot` for v1.1 stacks) for desktop widgets. `:app` wires those slots at composition time.
2. **Pure `:core:data` boundary with lease-guarded E5 sync**: `:core:data` holds zero UI coordinates or overlay routes. Instead, `PackageSyncCoordinator` exposes `acquireDragLease(draggedKey: ComponentKey?, onSourceRemoved: () -> Unit): AutoCloseable`. While a lease is open, external package deltas buffer in memory and flush in a single Room transaction as soon as the lease closes, immediately invoking `onSourceRemoved` if the dragged package disappears mid-drag (E5).
3. **Single launch gate in `:app`**: Every feature emits `onLaunchApp(ComponentKey, Rect?)` upward to `LauncherShell`, which checks `isLocked` and runs `BiometricAuthGateway` once before delegating to `LauncherAppsGateway.launch` and recording local usage (`app_usage_daily`).
4. **`ON_START` / `ON_STOP` `AppWidgetHost` lifecycle**: `LauncherActivity` registers `AppWidgetHostManager` as a `DefaultLifecycleObserver` so widgets update while visible (including translucent pause states) and consume zero background CPU when another app covers the launcher.
