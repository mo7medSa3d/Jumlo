package com.jumlo.app.ui.navigation

import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.components.MainDockTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** JVM contract: available on ordinary PR CI without a connected Android device. */
class MainDockContractTest {
    @Test fun bottomBarContainsExactlyTheRequestedFourDestinations() {
        assertEquals(
            listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS),
            MainDockTabs
        )
        assertEquals(4, MainDockTabs.distinct().size)
        assertFalse(MainDockTabs.contains(DockTab.DOWNLOADS))
        assertFalse(MainDockTabs.contains(DockTab.PRINT))
    }

    @Test fun downloadsRouteRemainsAvailableForHistoryAndNotifications() {
        assertEquals("downloads", DockTab.DOWNLOADS.route)
        assertEquals("home", MainDockTabs.first().route)
    }
}
