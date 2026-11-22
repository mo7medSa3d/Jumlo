package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeActionsPersistenceTest {
    @Test fun customOrderedHomeActionsSurviveRepositoryRecreationWithCanonicalMigration() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=UserPreferencesRepository(context)
        val original=repository.settingsFlow.first().quickActionIds
        try {
            repository.setQuickActionIds(listOf("img_01","pdf_06","pdf_07","dl_02","file_encrypted_zip"))
            val recreated=UserPreferencesRepository(context)
            assertEquals(
                listOf("img_04","pdf_05","pdf_long_image","dl_media","file_07"),
                recreated.settingsFlow.first().quickActionIds
            )
        } finally {repository.setQuickActionIds(original)}
    }
}
