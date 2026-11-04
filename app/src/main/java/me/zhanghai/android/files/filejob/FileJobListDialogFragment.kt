/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileJobListDialogBinding
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels

class FileJobListDialogFragment : AppCompatDialogFragment() {
    private val viewModel by viewModels { { FileJobListViewModel() } }

    private lateinit var binding: FileJobListDialogBinding

    private lateinit var adapter: FileJobListAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .apply {
                setTitle(R.string.file_job_list_title)

                binding = FileJobListDialogBinding.inflate(context.layoutInflater)
                binding.recyclerView.layoutManager = LinearLayoutManager(context)
                adapter = FileJobListAdapter { viewModel.cancel(it) }
                binding.recyclerView.adapter = adapter
                // Keep the same views for an update, so that they don't flicker for progress and
                // the live region only announces real changes.
                (binding.recyclerView.itemAnimator as SimpleItemAnimator)
                    .supportsChangeAnimations = false
                setView(binding.root)
            }
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.file_job_list_cancel_all, null)
            .create()
            .apply {
                // Override the listener here so that we won't close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        viewModel.cancelAll()
                    }
                    updateCancelAllButton()
                }
                viewModel.rowsLiveData.observe(this@FileJobListDialogFragment) {
                    onRowsChanged(it)
                }
            }

    private fun onRowsChanged(rows: List<FileJobRow>) {
        adapter.replace(rows, false)
        binding.emptyView.isVisible = rows.isEmpty()
        updateCancelAllButton()
    }

    private fun updateCancelAllButton() {
        // The button doesn't exist before the dialog is shown.
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled =
            viewModel.rowsLiveData.valueCompat.any { it.isActive }
    }

    companion object {
        fun show(fragment: Fragment) {
            FileJobListDialogFragment().show(fragment)
        }
    }
}
