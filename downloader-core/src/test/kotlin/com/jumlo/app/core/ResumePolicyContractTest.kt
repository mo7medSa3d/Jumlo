package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ResumePolicyContractTest {
    @Test fun parseRangeRejectsUnknownAndContradictoryLengths() {
        assertEquals(ByteRange(10, 19, 30), ResumePolicy.range("bytes 10-19/30"))
        assertNull(ResumePolicy.range("bytes 10-19/19"))
        assertNull(ResumePolicy.range("bytes 20-19/30"))
        assertNull(ResumePolicy.range("bytes 10-19"))
    }

    @Test fun validatesResumptionOnlyWithMatchingStrongValidatorAndCompleteRange() {
        val headers = mapOf(
            "etag" to "\"unchanged\"",
            "content-range" to "bytes 10-19/30",
            "content-length" to "10",
            "content-encoding" to "identity"
        )
        ResumePolicy.validate(10, "\"unchanged\"", null, 30, 206, headers)
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"previous\"", null, 30, 206, headers)
        }
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"unchanged\"", null, 30, 200, headers)
        }
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"unchanged\"", null, 30, 206, headers + ("content-range" to "bytes 10-19/*"))
        }
    }

    @Test fun onlyAcceptsFullInitialResponses() {
        ResumePolicy.validate(0, null, null, null, 200, emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            ResumePolicy.validate(0, null, null, null, 206, emptyMap())
        }
    }
}
