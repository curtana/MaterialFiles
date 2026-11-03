/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    // Whether an error dialog was answered with anything other than retrying. Only accessed on the
    // job thread.
    internal var hasUnresolvedError = false

    // Returns whether the job succeeded.
    fun runOn(service: FileJobService): Boolean {
        this.service = service
        return try {
            run()
            // TODO: Toast
            !hasUnresolvedError
        } catch (e: InterruptedIOException) {
            // TODO
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            service.showToast(e.toString())
            false
        } finally {
            service.notificationManager.cancel(id)
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()
}
