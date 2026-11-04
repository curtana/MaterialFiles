/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.content.Context
import android.view.View
import android.widget.Toast
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.showToast
import java.io.InterruptedIOException

sealed interface FileJobFailureDetail {
    /** The job was interrupted without being canceled, e.g. due to a network timeout. */
    object Interrupted : FileJobFailureDetail

    data class Error(val text: String) : FileJobFailureDetail
}

/** The content of the one toast for the end of a file job. */
data class FileJobEndNotice<T>(
    val outcome: FileJobOutcome,
    val detail: FileJobFailureDetail?,
    val cleanupFailures: List<T>
) {
    val isLong: Boolean
        get() = detail != null || cleanupFailures.isNotEmpty()
}

/** @return The content of the toast, or null if there should be no toast. */
fun <T> fileJobEndNoticeOf(
    notifiesCompletion: Boolean,
    outcome: FileJobOutcome,
    thrown: Throwable?,
    cleanupFailures: List<T>
): FileJobEndNotice<T>? =
    when (outcome) {
        FileJobOutcome.SUCCEEDED ->
            if (notifiesCompletion) {
                FileJobEndNotice(outcome, null, emptyList())
            } else {
                null
            }
        FileJobOutcome.CANCELED -> FileJobEndNotice(outcome, null, cleanupFailures)
        FileJobOutcome.FAILED -> {
            val detail = when (thrown) {
                null -> null
                is InterruptedIOException -> FileJobFailureDetail.Interrupted
                else -> FileJobFailureDetail.Error(thrown.toString())
            }
            FileJobEndNotice(outcome, detail, cleanupFailures)
        }
    }

internal fun showFileJobEndToast(
    context: Context,
    info: FileJobInfo,
    notice: FileJobEndNotice<Path>
) {
    val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val lines = mutableListOf(
        context.getString(
            R.string.file_job_end_toast_format, info.getTitle(context, isRtl),
            context.getString(notice.outcome.textRes)
        )
    )
    when (val detail = notice.detail) {
        null -> {}
        FileJobFailureDetail.Interrupted ->
            lines += context.getString(R.string.file_job_interrupted_toast)
        is FileJobFailureDetail.Error -> lines += detail.text
    }
    if (notice.cleanupFailures.isNotEmpty()) {
        lines += cleanupFailureTextOf(context, notice.cleanupFailures)
    }
    context.showToast(
        lines.joinToString("\n"), if (notice.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    )
}

internal fun cleanupFailureTextOf(context: Context, paths: List<Path>): String {
    val path = paths.first()
    val name = path.plainFileJobName()
    val parentName = (path.parent ?: path).plainFileJobName()
    val remainingCount = paths.size - 1
    return if (remainingCount == 0) {
        context.getString(R.string.file_job_cleanup_failed_toast_format, name, parentName)
    } else {
        context.getQuantityString(
            R.plurals.file_job_cleanup_failed_multiple_toast_format, remainingCount, name,
            parentName, remainingCount
        )
    }
}

internal val FileJobOutcome.textRes: Int
    get() =
        when (this) {
            FileJobOutcome.SUCCEEDED -> R.string.file_job_list_outcome_succeeded
            FileJobOutcome.CANCELED -> R.string.file_job_list_outcome_canceled
            FileJobOutcome.FAILED -> R.string.file_job_list_outcome_failed
        }
