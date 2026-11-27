package com.jumlo.app.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragDropTargetResolverTest {
    @Test fun explicitHoveredFolderWinsOverCurrentDirectory() {
        assertEquals(
            "folder-B",
            DragDropTargetResolver.resolve(
                hoveredTargetId = "folder-B",
                startFolderId = null,
                currentFolderId = "folder-A"
            )
        )
    }

    @Test fun droppingInsideNavigatedFolderPlacesItemIntoThatFolder() {
        assertEquals(
            "folder-A",
            DragDropTargetResolver.resolve(
                hoveredTargetId = null,
                startFolderId = null,
                currentFolderId = "folder-A"
            )
        )
        assertEquals(
            "folder-nested",
            DragDropTargetResolver.resolve(
                hoveredTargetId = null,
                startFolderId = "folder-A",
                currentFolderId = "folder-nested"
            )
        )
    }

    @Test fun droppingAfterNavigatingBackToRootTargetsRootFolder() {
        assertEquals(
            ROOT_DROP_TARGET_ID,
            DragDropTargetResolver.resolve(
                hoveredTargetId = null,
                startFolderId = "folder-A",
                currentFolderId = null
            )
        )
    }

    @Test fun droppingOnEmptySpaceInSameFolderCancelsMove() {
        assertNull(
            DragDropTargetResolver.resolve(
                hoveredTargetId = null,
                startFolderId = "folder-A",
                currentFolderId = "folder-A"
            )
        )
        assertNull(
            DragDropTargetResolver.resolve(
                hoveredTargetId = null,
                startFolderId = null,
                currentFolderId = null
            )
        )
    }
}
