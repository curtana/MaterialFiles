/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.text.BidiFormatter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileJobListItemBinding
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.ui.ListAdapter
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.layoutInflater

class FileJobListAdapter(
    private val onCancel: (Int) -> Unit
) : ListAdapter<FileJobRow, FileJobListAdapter.ViewHolder>(CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            FileJobListItemBinding.inflate(parent.context.layoutInflater, parent, false),
            parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        ).apply {
            binding.cancelButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCancel(getItem(position).id)
                }
            }
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        val context = binding.root.context
        val row = getItem(position)
        binding.titleText.text = row.info.getTitle(context, holder.isRtl)
        binding.cancelButton.isEnabled = row.isActive
        binding.cancelButton.alpha = if (row.isActive) 1f else DISABLED_ALPHA
        binding.cancelButton.contentDescription = context.getString(
            R.string.file_job_list_cancel_description_format, row.info.getSubjectText(context)
        )
        val status = row.status
        if (status is FileJobRowStatus.Finished) {
            binding.statusText.isVisible = false
            binding.progress.isVisible = false
            binding.outcomeText.isVisible = true
            // Setting the same text again would make the live region announce it again.
            val outcomeText = status.getText(context)
            if (binding.outcomeText.text.toString() != outcomeText) {
                binding.outcomeText.text = outcomeText
            }
        } else {
            binding.outcomeText.isVisible = false
            binding.statusText.isVisible = true
            binding.statusText.text = status.getText(context)
            binding.progress.isVisible = true
            val percent = when (status) {
                is FileJobRowStatus.Size -> status.percent
                is FileJobRowStatus.Count -> status.percent
                else -> null
            }
            if (percent != null) {
                binding.progress.setProgressCompat(percent, false)
            } else {
                binding.progress.isIndeterminate = true
            }
        }
    }

    class ViewHolder(
        val binding: FileJobListItemBinding,
        val isRtl: Boolean
    ) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val DISABLED_ALPHA = 0.38f

        private val CALLBACK = object : DiffUtil.ItemCallback<FileJobRow>() {
            override fun areItemsTheSame(oldItem: FileJobRow, newItem: FileJobRow): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FileJobRow, newItem: FileJobRow): Boolean =
                oldItem == newItem
        }
    }
}

private fun FileJobInfo.getSubjectText(context: Context): String {
    val subjectName = BidiFormatter.getInstance().unicodeWrap(subjectName)
    return if (subjectCount <= 1) {
        subjectName
    } else {
        val remainingCount = subjectCount - 1
        context.getQuantityString(
            R.plurals.file_job_list_subject_multiple_format, remainingCount, subjectName,
            remainingCount
        )
    }
}

internal fun FileJobInfo.getTitle(context: Context, isRtl: Boolean): String {
    val subjectText = getSubjectText(context)
    val targetText = if (destinationName != null) {
        context.getString(
            if (isRtl) {
                R.string.file_job_list_destination_format_rtl
            } else {
                R.string.file_job_list_destination_format
            }, subjectText, BidiFormatter.getInstance().unicodeWrap(destinationName)
        )
    } else {
        subjectText
    }
    return context.getString(
        R.string.file_job_list_title_format, context.getString(type.textRes), targetText
    )
}

private val FileJobType.textRes: Int
    get() =
        when (this) {
            FileJobType.COPY -> R.string.file_job_list_type_copy
            FileJobType.EXTRACT -> R.string.file_job_list_type_extract
            FileJobType.MOVE -> R.string.file_job_list_type_move
            FileJobType.ARCHIVE -> R.string.file_job_list_type_archive
            FileJobType.DELETE -> R.string.file_job_list_type_delete
            FileJobType.CREATE -> R.string.file_job_list_type_create
            FileJobType.RENAME -> R.string.file_job_list_type_rename
            FileJobType.OPEN -> R.string.file_job_list_type_open
            FileJobType.INSTALL_APK -> R.string.file_job_list_type_install_apk
            FileJobType.SAVE -> R.string.file_job_list_type_save
            FileJobType.WRITE -> R.string.file_job_list_type_write
            FileJobType.SET_OWNER -> R.string.file_job_list_type_set_owner
            FileJobType.SET_GROUP -> R.string.file_job_list_type_set_group
            FileJobType.SET_MODE -> R.string.file_job_list_type_set_mode
            FileJobType.SET_SELINUX_CONTEXT -> R.string.file_job_list_type_set_selinux_context
            FileJobType.RESTORE_SELINUX_CONTEXT ->
                R.string.file_job_list_type_restore_selinux_context
        }

private fun FileJobRowStatus.getText(context: Context): String =
    when (this) {
        FileJobRowStatus.Pending -> context.getString(R.string.file_job_list_status_pending)
        FileJobRowStatus.Running -> context.getString(R.string.file_job_list_status_running)
        is FileJobRowStatus.Scanning ->
            context.getQuantityString(
                R.plurals.file_job_list_status_scanning_format, fileCount, fileCount,
                size.asFileSize().formatHumanReadable(context)
            )
        is FileJobRowStatus.Size -> {
            val transferredSizeText = transferredSize.asFileSize().formatHumanReadable(context)
            val sizeText = size.asFileSize().formatHumanReadable(context)
            if (fileIndex != null) {
                context.getString(
                    R.string.file_job_list_status_size_multiple_format, percent,
                    transferredSizeText, sizeText, fileIndex, fileCount
                )
            } else {
                context.getString(
                    R.string.file_job_list_status_size_format, percent, transferredSizeText,
                    sizeText
                )
            }
        }
        is FileJobRowStatus.Count ->
            context.getString(
                R.string.file_job_list_status_count_format, percent, fileIndex, fileCount
            )
        is FileJobRowStatus.Finished -> {
            val outcomeText = context.getString(outcome.textRes)
            if (hasCleanupFailure) {
                context.getString(
                    R.string.file_job_list_outcome_cleanup_failed_format, outcomeText
                )
            } else {
                outcomeText
            }
        }
    }
