package com.jumlo.app.model

/** UI interaction preferences are resolved without mutating Android accessibility settings. */
object AppFeedbackPolicy {
    /** System Remove animations always wins; the user's Jumlo preference is additive. */
    fun reducedMotion(systemReduced: Boolean, userRequested: Boolean): Boolean =
        systemReduced || userRequested

    /** OFF must suppress vibration even when a stale enabled preference survives migration. */
    fun hapticsEnabled(userEnabled: Boolean, intensity: HapticIntensity): Boolean =
        userEnabled && intensity != HapticIntensity.OFF
}
