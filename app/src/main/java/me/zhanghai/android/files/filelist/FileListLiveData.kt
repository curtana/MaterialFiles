/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import me.zhanghai.android.files.app.mainExecutor
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.concurrent.ExecutorService

class FileListLiveData(private val path: Path) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private val loader = FileListLoader(
        AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService, mainExecutor, ::loadFileList,
        ::onLoadStarted, ::onLoadFinished, ::onChangeObserved
    )

    private var explicitLoadFailure: Failure<List<FileItem>>? = null

    private val observer: PathObserver

    @Volatile
    private var isChangedWhileInactive = false

    init {
        loadValue()
        observer = PathObserver(path) { onChangeObserved() }
    }

    // An explicit load, which always shows the loading state.
    fun loadValue() {
        loader.reload()
    }

    // A load for a change doesn't show the loading state if there is already a list shown.
    private fun onLoadStarted(isForChange: Boolean) {
        if (!shouldLoadSilently(isForChange, value)) {
            value = Loading(value?.value)
        }
    }

    private fun onLoadFinished(value: Stateful<List<FileItem>>, isForChange: Boolean) {
        explicitLoadFailure = if (!isForChange && value is Failure) value else null
        this.value = value
    }

    fun isExplicitLoadFailure(stateful: Stateful<List<FileItem>>): Boolean =
        stateful === explicitLoadFailure

    private fun loadFileList(): Stateful<List<FileItem>> =
        try {
            path.newDirectoryStream().use { directoryStream ->
                val fileList = mutableListOf<FileItem>()
                for (path in directoryStream) {
                    try {
                        fileList.add(path.loadFileItem())
                    } catch (e: DirectoryIteratorException) {
                        // TODO: Ignoring such a file can be misleading and we need to support
                        //  files without information.
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                Success(fileList as List<FileItem>)
            }
        } catch (e: Exception) {
            Failure(valueCompat.value, e)
        }

    private fun onChangeObserved() {
        if (hasActiveObservers()) {
            loader.requestReloadOnChange()
        } else {
            isChangedWhileInactive = true
        }
    }

    override fun onActive() {
        if (isChangedWhileInactive) {
            isChangedWhileInactive = false
            loader.requestReloadOnChange()
        }
    }

    override fun close() {
        observer.close()
        loader.close()
    }
}
