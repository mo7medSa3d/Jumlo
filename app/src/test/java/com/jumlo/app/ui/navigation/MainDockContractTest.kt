package com.jumlo.app.ui.navigation

import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.components.MainDockTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The main dock must stay focused; Downloads remains reachable off the dock. */
class MainDockContractTest {
    @Test fun onlyHomeToolsFilesSettingsArePrimaryTabs() {
        assertEquals(
            listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS),
            MainDockTabs
        )
        assertFalse(MainDockTabs.contains(DockTab.DOWNLOADS))
        assertFalse(MainDockTabs.contains(DockTab.PRINT))
    }
}
