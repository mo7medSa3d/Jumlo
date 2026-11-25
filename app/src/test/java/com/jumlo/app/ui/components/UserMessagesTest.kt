package com.jumlo.app.ui.components

import com.jumlo.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessagesTest {

    @Test
    fun documentUnreadableAndOfficeCodesMapToExpectedResources() {
        assertEquals(R.string.viewer_office_error_open, userMessageResource("DOCUMENT_UNREADABLE"))
        assertEquals(R.string.rebuild_office_invalid, userMessageResource("OFFICE_INVALID"))
        assertEquals(R.string.rebuild_office_empty, userMessageResource("OFFICE_NO_CONTENT"))
        assertEquals(R.string.msg_size_limit, userMessageResource("DOCUMENT_TOO_LARGE"))
        assertEquals(R.string.msg_access_lost, userMessageResource("CONTENT_URI_REQUIRED"))
    }

    @Test
    fun markupAndDestinationCodesMapToExpectedResources() {
        assertEquals(R.string.s10_saved_in_place, userMessageResource("MARKUP_SAVED_IN_PLACE"))
        assertEquals(R.string.s10_saved_as_copy, userMessageResource("MARKUP_SAVED_AS_COPY"))
        assertEquals(R.string.s10_markup_save_failed, userMessageResource("MARKUP_SAVE_FAILED"))
        assertEquals(R.string.msg_destination_save_failed, userMessageResource("DESTINATION_SAVE_FAILED"))
    }

    @Test
    fun unknownAndBlankCodesFallBackToGenericFailureMessage() {
        assertEquals(R.string.msg_failed, userMessageResource("UNRECOGNIZED_ENGINE_CODE"))
        assertEquals(R.string.msg_failed, userMessageResource(""))
    }
}
