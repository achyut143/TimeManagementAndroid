package com.focusflow.app.domain

import com.focusflow.app.data.BacklogFolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeTest {
    private val work = BacklogFolderEntity(id = "work", name = "Work", sortOrder = 1)
    private val q4 = BacklogFolderEntity(id = "q4", name = "Q4", parentId = "work")
    private val ideas = BacklogFolderEntity(id = "ideas", name = "Ideas", parentId = "q4")
    private val home = BacklogFolderEntity(id = "home", name = "Home", sortOrder = 2)
    private val all = listOf(work, q4, ideas, home)

    @Test fun pathJoinsAncestors() = assertEquals("Work › Q4 › Ideas", FolderTree.path(ideas, all))

    @Test fun descendantsAtAnyDepth() = assertEquals(setOf("q4", "ideas"), FolderTree.descendants("work", all))

    @Test fun topLevelChildrenInOrder() = assertEquals(listOf(work, home), FolderTree.children(null, all))

    @Test fun cannotMoveIntoItselfOrInside() =
        assertEquals(listOf(home), FolderTree.moveTargets(work, all))

    @Test fun loopingParentsStop() {
        val a = BacklogFolderEntity(id = "a", name = "A", parentId = "b")
        val b = BacklogFolderEntity(id = "b", name = "B", parentId = "a")
        assertEquals(2, FolderTree.chain("a", listOf(a, b)).size)
    }
}
