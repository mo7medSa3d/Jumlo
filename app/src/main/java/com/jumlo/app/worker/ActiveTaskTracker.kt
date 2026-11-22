package com.jumlo.app.worker

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe tracker of foreground state and active navigation route/job/tool so workers
 * suppress completion/failure alerts only when the user is already watching that task.
 */
object ActiveTaskTracker {
    private data class NavState(
        val route: String = "",
        val activeJobId: String? = null,
        val activeToolId: String? = null
    )

    @Volatile
    var isAppForeground: Boolean = false
        private set

    private val navState = AtomicReference(NavState())

    val currentRoute: String
        get() = navState.get().route

    val activeJobId: String?
        get() = navState.get().activeJobId

    val activeToolId: String?
        get() = navState.get().activeToolId

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
    }

    fun updateNavigation(route: String, jobId: String? = null, toolId: String? = null) {
        navState.set(
            NavState(
                route = route,
                activeJobId = if (route == "activity" || route == "workspace") jobId else null,
                activeToolId = if (route == "workspace") toolId?.takeIf { it.isNotBlank() } else null
            )
        )
    }

    fun shouldNotifyCompletion(jobId: String, toolId: String? = null): Boolean {
        if (!isAppForeground) return true
        val state = navState.get()
        if (state.route == "downloads") return false
        if (state.route == "activity" && (state.activeJobId == null || state.activeJobId == jobId)) return false
        if (state.route == "workspace" && (
                (state.activeJobId != null && state.activeJobId == jobId) ||
                (!toolId.isNullOrBlank() && state.activeToolId == toolId)
            )
        ) return false
        return true
    }
}
