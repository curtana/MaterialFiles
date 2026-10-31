/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.text.BidiFormatter
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.CopyMoveType
import me.zhanghai.android.files.filejob.getResourceId
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.putArgs

class ConfirmCopyMoveDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val confirmation = createCopyMoveConfirmation(args.files, args.targetDirectory)
        val title = getString(
            args.type.getResourceId(
                R.string.file_list_confirm_copy_move_copy_title,
                R.string.file_list_confirm_copy_move_extract_title,
                R.string.file_list_confirm_copy_move_move_title
            )
        )
        val bidiFormatter = BidiFormatter.getInstance()
        val singleItemName = confirmation.singleItemName
        val itemLine = if (singleItemName != null) {
            getString(
                R.string.file_list_confirm_copy_move_item_name_format,
                bidiFormatter.unicodeWrap(singleItemName)
            )
        } else {
            getQuantityString(
                R.plurals.file_list_confirm_copy_move_item_count_format, confirmation.itemCount,
                confirmation.itemCount
            )
        }
        var from = confirmation.firstLocation?.let {
            bidiFormatter.unicodeWrap(it.toLocationDisplayString())
        }
        if (from != null && confirmation.otherLocationCount > 0) {
            from = getQuantityString(
                R.plurals.file_list_confirm_copy_move_locations_format,
                confirmation.otherLocationCount, from, confirmation.otherLocationCount
            )
        }
        val to = bidiFormatter.unicodeWrap(confirmation.targetDirectory.toLocationDisplayString())
        val locationLine = if (from != null) {
            getString(R.string.file_list_confirm_copy_move_from_to_format, from, to)
        } else {
            getString(R.string.file_list_confirm_copy_move_to_format, to)
        }
        val notice = when (confirmation.sameDirectoryNotice) {
            SameDirectoryNotice.NONE -> null
            SameDirectoryNotice.ALL ->
                getString(R.string.file_list_confirm_copy_move_same_directory_all)
            SameDirectoryNotice.SOME ->
                getQuantityString(
                    R.plurals.file_list_confirm_copy_move_same_directory_some_format,
                    confirmation.sameDirectoryCount, confirmation.sameDirectoryCount
                )
        }
        val message = buildString {
            append(itemLine)
            append("\n")
            append(locationLine)
            if (notice != null) {
                append("\n\n")
                append(notice)
            }
        }
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.file_list_confirm_copy_move_positive) { _, _ ->
                listener.copyMoveFiles(
                    args.type, confirmation.sources, confirmation.targetDirectory, args.files,
                    args.origin
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private val TAG = ConfirmCopyMoveDialogFragment::class.java.name

        fun show(
            copy: Boolean,
            files: FileItemSet,
            targetDirectory: Path,
            origin: Origin,
            fragment: Fragment
        ) {
            if (files.isEmpty()) {
                return
            }
            val fragmentManager = fragment.childFragmentManager
            // Don't stack another dialog while one is already shown.
            if (fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }
            // Freeze the files because the paste state and the selection are mutated in place.
            val frozenFiles = FileItemSet().apply { addAll(files) }
            // showNow() so that the tag check above sees this dialog right away.
            ConfirmCopyMoveDialogFragment()
                .putArgs(
                    Args(getCopyMoveType(copy, frozenFiles), frozenFiles, targetDirectory, origin)
                )
                .showNow(fragmentManager, TAG)
        }
    }

    @Parcelize
    class Args(
        val type: CopyMoveType,
        val files: FileItemSet,
        val targetDirectory: @WriteWith<ParcelableParceler> Path,
        val origin: Origin
    ) : ParcelableArgs

    enum class Origin {
        PASTE,
        OTHER_PANE
    }

    interface Listener {
        fun copyMoveFiles(
            type: CopyMoveType,
            sources: List<Path>,
            targetDirectory: Path,
            files: FileItemSet,
            origin: Origin
        )
    }
}
