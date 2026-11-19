/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// Pure parts of network copies (#55 B2): link depth, resuming a walk, and error decisions.

package me.zhanghai.android.files.filejob

import java.io.IOException
import java8.nio.file.Path

/**
 * The directories a following-links walk is in (#55 B2): their link depths and, for resuming a
 * directory whose listing failed midway, the children already visited. Not thread safe.
 */
internal class LinkWalkState(private val baseDepth: Int = 0) {
    private class Frame(val depth: Int, val visitedChildren: MutableSet<Path>)

    private val frames = ArrayList<Frame>()

    val currentDepth: Int
        get() = frames.lastOrNull()?.depth ?: baseDepth

    fun depthOf(isSymbolicLink: Boolean): Int =
        if (isSymbolicLink) currentDepth + 1 else currentDepth

    fun isExceeded(depth: Int): Boolean = depth > MAX_LINK_DEPTH

    fun markVisited(path: Path): Boolean = frames.lastOrNull()?.visitedChildren?.add(path) ?: true

    fun enter(depth: Int, visitedChildren: Set<Path> = emptySet()) {
        frames += Frame(depth, visitedChildren.toMutableSet())
    }

    fun exit(): ExitedDirectory {
        check(frames.isNotEmpty()) { "No directory to exit" }
        val frame = frames.removeAt(frames.lastIndex)
        return ExitedDirectory(frame.depth, frame.visitedChildren)
    }
}

internal class ExitedDirectory(val depth: Int, val visitedChildren: Set<Path>)

/**
 * The failure of the start of a walk, passed back to the loop that asks about it (#55 B2).
 *
 * @param enteredDepth the depth of the directory if it was already entered (and its target
 * created), or null
 * @param visitedChildren the children already visited, for resuming the directory
 */
internal class ItemWalkFailure(
    val error: IOException,
    val enteredDepth: Int?,
    val visitedChildren: Set<Path>
) {
    // An entered directory was already counted as transferred, while an item not entered was
    // counted by the scan as one item of size 0.
    val isCountedOnSkip: Boolean
        get() = enteredDepth == null
}

/**
 * The failure of a walk start that failed before being entered. A start being resumed (#55 ③)
 * keeps what the previous failure knew, so the next retry still doesn't copy it again.
 */
internal fun startFailureBeforeEntering(error: IOException, resume: ItemWalkFailure?): ItemWalkFailure =
    ItemWalkFailure(error, resume?.enteredDepth, resume?.visitedChildren ?: emptySet())

/** Enters [directory] without copying it again if it is a resumed start; false otherwise. */
internal fun LinkWalkState.enterIfResumedStart(
    directory: Path,
    start: Path,
    resume: ItemWalkFailure?
): Boolean {
    val resumed = resume ?: return false
    val depth = resumed.enteredDepth ?: return false
    if (directory != start) {
        return false
    }
    enter(depth, resumed.visitedChildren)
    return true
}

internal const val MAX_LINK_DEPTH = 8
