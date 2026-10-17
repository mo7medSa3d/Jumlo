package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeActionsPersistenceTest {
    @Test fun customOrderedHomeActionsSurviveRepositoryRecreation() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=UserPreferencesRepository(context)
        val original=repository.settingsFlow.first().quickActionIds
        try {
            repository.setQuickActionIds(listOf("sha256","img_04","pdf_03","dl_02"))
            val recreated=UserPreferencesRepository(context)
            assertEquals(listOf("sha256","img_04","pdf_03","dl_02"),recreated.settingsFlow.first().quickActionIds)
        } finally {repository.setQuickActionIds(original)}
    }
}
