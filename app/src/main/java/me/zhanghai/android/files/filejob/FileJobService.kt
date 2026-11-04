/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.MainThread
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.util.ForegroundNotificationManager
import me.zhanghai.android.files.util.WakeWifiLock
import me.zhanghai.android.files.util.removeFirst
import java.util.concurrent.Executors
import java.util.concurrent.Future
import me.zhanghai.android.files.compat.removeFirstCompat

class FileJobService : Service() {
    private lateinit var wakeWifiLock: WakeWifiLock

    internal lateinit var notificationManager: ForegroundNotificationManager
        private set

    private val executorService = Executors.newCachedThreadPool()

    private val runningJobs = mutableMapOf<FileJob, Future<*>>()

    override fun onCreate() {
        super.onCreate()

        wakeWifiLock = WakeWifiLock(FileJobService::class.java.simpleName)
        notificationManager = ForegroundNotificationManager(this)
        instance = this

        while (pendingJobs.isNotEmpty()) {
            val job = pendingJobs.removeFirstCompat()
            FileJobStateTracker.onJobRunning(job.id)
            startJob(job)
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private val jobCount: Int
        get() = synchronized(runningJobs) { runningJobs.size }

    private fun startJob(job: FileJob) {
        // Synchronize on runningJobs to prevent a job from removing itself before being added.
        synchronized(runningJobs) {
            val future = executorService.submit {
                // The cleanup must also happen if the job throws something other than an
                // exception, so that it doesn't stay running and keep the lock acquired.
                runJobAndReport({ job.runOn(this) }) { bodyOutcome ->
                    synchronized(runningJobs) {
                        runningJobs.remove(job)
                        updateWakeWifiLockLocked()
                    }
                    // The job can no longer be canceled here, so the end of the job agrees with
                    // what is reported if it was canceled.
                    val outcome = job.finishEnd(bodyOutcome)
                    mainExecutorCompat.execute {
                        FileJobStateTracker.onJobFinished(job.id, outcome)
                    }
                }
            }
            runningJobs[job] = future
            updateWakeWifiLockLocked()
        }
    }

    private fun cancelJob(id: Int) {
        val entry = synchronized(runningJobs) {
            runningJobs.removeFirst { it.key.id == id }
                .also {
                    it?.key?.isCanceled = true
                    it?.value?.cancel(true)
                    updateWakeWifiLockLocked()
                }
        }
        // The job thread may report later, which is ignored.
        if (entry != null) {
            entry.key.notifyCanceledWithoutRun(this)
            FileJobStateTracker.onJobFinished(id, FileJobOutcome.CANCELED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null

        val canceledJobs = mutableListOf<FileJob>()
        synchronized(runningJobs) {
            while (runningJobs.isNotEmpty()) {
                val entry = runningJobs.removeFirst()
                entry.key.isCanceled = true
                entry.value.cancel(true)
                canceledJobs += entry.key
            }
            updateWakeWifiLockLocked()
        }
        canceledJobs.forEach {
            it.notifyCanceledWithoutRun(this)
            FileJobStateTracker.onJobFinished(it.id, FileJobOutcome.CANCELED)
        }
    }

    // Synchronize on runningJobs to avoid the potential race condition that the lock is
    // acquired after all jobs are finished in a very short time.
    private fun updateWakeWifiLockLocked() {
        wakeWifiLock.isAcquired = jobCount > 0
    }

    companion object {
        private var instance: FileJobService? = null

        private val pendingJobs = mutableListOf<FileJob>()

        val runningJobCount: Int
            @MainThread
            get() = instance?.jobCount ?: 0

        @MainThread
        private fun startJob(job: FileJob, info: FileJobInfo, context: Context) {
            job.info = info
            val instance = instance
            FileJobStateTracker.onJobStarted(job.id, info, instance == null)
            if (instance != null) {
                instance.startJob(job)
            } else {
                pendingJobs.add(job)
                context.startService(Intent(context, FileJobService::class.java))
            }
        }

        fun archive(
            sources: List<Path>,
            archiveFile: Path,
            format: Int,
            filter: Int,
            password: String?,
            context: Context
        ) {
            startJob(
                ArchiveFileJob(sources, archiveFile, format, filter, password),
                FileJobInfo.of(FileJobType.ARCHIVE, sources, archiveFile), context
            )
        }

        fun copy(sources: List<Path>, targetDirectory: Path, context: Context) {
            startJob(
                CopyFileJob(sources, targetDirectory),
                FileJobInfo.of(
                    copyTypeOf(sources.all { it.isArchivePath }), sources, targetDirectory
                ),
                context
            )
        }

        fun create(path: Path, createDirectory: Boolean, context: Context) {
            startJob(
                CreateFileJob(path, createDirectory),
                FileJobInfo.of(FileJobType.CREATE, listOf(path), null), context
            )
        }

        fun delete(paths: List<Path>, context: Context) {
            startJob(DeleteFileJob(paths), FileJobInfo.of(FileJobType.DELETE, paths, null), context)
        }

        fun move(sources: List<Path>, targetDirectory: Path, context: Context) {
            startJob(
                MoveFileJob(sources, targetDirectory),
                FileJobInfo.of(FileJobType.MOVE, sources, targetDirectory), context
            )
        }

        fun installApk(file: Path, context: Context) {
            startJob(
                InstallApkJob(file), FileJobInfo.of(FileJobType.INSTALL_APK, listOf(file), null),
                context
            )
        }

        fun open(file: Path, mimeType: MimeType, withChooser: Boolean, context: Context) {
            startJob(
                OpenFileJob(file, mimeType, withChooser),
                FileJobInfo.of(FileJobType.OPEN, listOf(file), null), context
            )
        }

        fun rename(path: Path, newName: String, context: Context) {
            startJob(
                RenameFileJob(path, newName),
                FileJobInfo.of(FileJobType.RENAME, listOf(path), null),
                context
            )
        }

        fun restoreSeLinuxContext(path: Path, recursive: Boolean, context: Context) {
            startJob(
                RestoreFileSeLinuxContextJob(path, recursive),
                FileJobInfo.of(
                    FileJobType.RESTORE_SELINUX_CONTEXT, listOf(path), null, recursive
                ), context
            )
        }

        fun save(source: Path, target: Path, context: Context) {
            startJob(
                SaveFileJob(source, target),
                FileJobInfo.of(FileJobType.SAVE, listOf(source), target.parent), context
            )
        }

        fun setGroup(path: Path, group: PosixGroup, recursive: Boolean, context: Context) {
            startJob(
                SetFileGroupJob(path, group, recursive),
                FileJobInfo.of(FileJobType.SET_GROUP, listOf(path), null, recursive), context
            )
        }

        fun setMode(
            path: Path,
            mode: Set<PosixFileModeBit>,
            recursive: Boolean,
            uppercaseX: Boolean,
            context: Context
        ) {
            startJob(
                SetFileModeJob(path, mode, recursive, uppercaseX),
                FileJobInfo.of(FileJobType.SET_MODE, listOf(path), null, recursive), context
            )
        }

        fun setOwner(path: Path, owner: PosixUser, recursive: Boolean, context: Context) {
            startJob(
                SetFileOwnerJob(path, owner, recursive),
                FileJobInfo.of(FileJobType.SET_OWNER, listOf(path), null, recursive), context
            )
        }

        fun setSeLinuxContext(
            path: Path,
            seLinuxContext: String,
            recursive: Boolean,
            context: Context
        ) {
            startJob(
                SetFileSeLinuxContextJob(path, seLinuxContext, recursive),
                FileJobInfo.of(FileJobType.SET_SELINUX_CONTEXT, listOf(path), null, recursive),
                context
            )
        }

        fun write(
            file: Path,
            content: ByteArray,
            context: Context,
            listener: ((Boolean) -> Unit)?
        ) {
            startJob(
                WriteFileJob(file, content, listener),
                FileJobInfo.of(FileJobType.WRITE, listOf(file), null), context
            )
        }

        @MainThread
        fun cancelJob(id: Int) {
            val pendingJob = pendingJobs.removeFirst { it.id == id }
            if (pendingJob != null) {
                pendingJob.notifyCanceledWithoutRun(application)
                FileJobStateTracker.onJobFinished(id, FileJobOutcome.CANCELED)
            }
            instance?.cancelJob(id)
        }
    }
}

/**
 * Runs a job and then always calls [onFinished] once, with failed if the job threw. Whatever the
 * job threw is rethrown.
 */
internal fun runJobAndReport(run: () -> FileJobOutcome, onFinished: (FileJobOutcome) -> Unit) {
    var outcome = FileJobOutcome.FAILED
    try {
        outcome = run()
    } finally {
        onFinished(outcome)
    }
}
