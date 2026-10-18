package com.jumlo.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLifecyclePolicyTest {
    @Test fun userPauseIsNotScheduledOnAppRecovery() {
        assertFalse(JobLifecyclePolicy.shouldRecover("PAUSED"))
        assertTrue(JobLifecyclePolicy.shouldRecover("INTERRUPTED"))
        assertTrue(JobLifecyclePolicy.shouldRecover("RUNNING"))
        assertFalse(JobLifecyclePolicy.shouldRecover("CANCELLED"))
        assertFalse(JobLifecyclePolicy.shouldRecover("WAITING_FOR_INPUT"))
    }

    @Test fun pausingIsNotAllowedDuringFinalPublication() {
        assertTrue(JobLifecyclePolicy.canPause("QUEUED"))
        assertTrue(JobLifecyclePolicy.canPause("VALIDATING"))
        assertTrue(JobLifecyclePolicy.canPause("RUNNING"))
        assertFalse(JobLifecyclePolicy.canPause("FINALIZING"))
        assertFalse(JobLifecyclePolicy.canPause("COMPLETED"))
    }
}
