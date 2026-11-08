package com.focusflow.app.domain

import com.focusflow.app.data.BacklogFolderEntity

/**
 * Walking the backlog folder tree (port of iOS `FolderTree`). Every walk
 * stops on a repeat, so a bad parent link can never loop forever.
 */
object FolderTree {
    /** The folder with [id], then its parent, grandparent… up to the top. */
    fun chain(id: String?, folders: List<BacklogFolderEntity>): List<BacklogFolderEntity> {
        val byId = folders.associateBy { it.id }
        val result = mutableListOf<BacklogFolderEntity>()
        val seen = mutableSetOf<String>()
        var current = id
        while (current != null && seen.add(current)) {
            val folder = byId[current] ?: break
            result += folder
            current = folder.parentId
        }
        return result
    }

    /** "Work › Q4 › Ideas" */
    fun path(folder: BacklogFolderEntity, folders: List<BacklogFolderEntity>): String =
        chain(folder.id, folders).asReversed().joinToString(" › ") { it.name }

    /** Ids of every folder inside [id], at any depth (not including [id]). */
    fun descendants(id: String, folders: List<BacklogFolderEntity>): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val next = queue.removeLast()
            for (child in folders) {
                if (child.parentId == next && child.id != id && result.add(child.id)) queue += child.id
            }
        }
        return result
    }

    /**
     * Folders directly inside [id], in display order. For null: top-level
     * folders, plus any whose parent no longer exists.
     */
    fun children(id: String?, folders: List<BacklogFolderEntity>): List<BacklogFolderEntity> {
        val ids = folders.map { it.id }.toSet()
        return folders
            .filter { f -> if (id != null) f.parentId == id else f.parentId == null || f.parentId !in ids }
            .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
    }

    /** Folders that [folder] may move into: not itself or anything inside it. */
    fun moveTargets(folder: BacklogFolderEntity, folders: List<BacklogFolderEntity>): List<BacklogFolderEntity> {
        val blocked = descendants(folder.id, folders) + folder.id
        return folders.filter { it.id !in blocked }
    }

    /** All folders with their full path, sorted by path, for pickers. */
    fun sortedByPath(folders: List<BacklogFolderEntity>): List<Pair<BacklogFolderEntity, String>> =
        folders.map { it to path(it, folders) }.sortedBy { it.second.lowercase() }
}
