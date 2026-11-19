/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java8.nio.file.LinkOption
import java8.nio.file.StandardCopyOption
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.SkipAttributesCopyOption
import me.zhanghai.android.files.provider.common.toCopyOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyClassificationTest {
    private val linux = CopyLocation.Linux
    private val deviceStorage =
        CopyLocation.Document(true, "com.android.externalstorage.documents")
    private val bugReport = CopyLocation.Document(true, "com.android.shell.documents")
    private val mtp = CopyLocation.Document(true, "com.android.mtp.documents")
    private val cloud = CopyLocation.Document(false, "com.google.android.apps.docs.storage")
    private val nullAuthority = CopyLocation.Document(false, null)
    private val archiveOnPhone = CopyLocation.Archive(linux)
    private val archiveOnDeviceStorage = CopyLocation.Archive(deviceStorage)
    private val archiveInArchiveOnPhone = CopyLocation.Archive(CopyLocation.Archive(linux))
    private val archiveOnNas = CopyLocation.Archive(CopyLocation.Other)
    private val archiveOnMtp = CopyLocation.Archive(mtp)
    private val other = CopyLocation.Other

    // SRS 2.1 option A: location to whether it is the phone.
    private val table = listOf(
        linux to true,
        deviceStorage to true,
        bugReport to true,
        mtp to false,
        cloud to false,
        nullAuthority to false,
        archiveOnPhone to true,
        archiveOnDeviceStorage to true,
        archiveInArchiveOnPhone to true,
        archiveOnNas to false,
        archiveOnMtp to false,
        other to false
    )

    // U-A01
    @Test
    fun isPhone_optionATable() {
        assertEquals(12, table.size)
        for ((location, isPhone) in table) {
            assertEquals(location.toString(), isPhone, location.isPhone)
        }
    }

    // U-A02
    @Test
    fun classify_allPairs() {
        for ((source, isSourcePhone) in table) {
            for ((targetDirectory, isTargetPhone) in table) {
                val expected = if (isSourcePhone && isTargetPhone) {
                    CopyKind.PHONE_TO_PHONE
                } else {
                    CopyKind.NETWORK
                }
                assertEquals(
                    "($source, $targetDirectory)", expected, classifyCopy(source, targetDirectory)
                )
            }
        }
    }

    // U-A03
    @Test
    fun classify_directionDoesNotMatter() {
        for ((source, targetDirectory) in listOf(
            linux to other, other to linux, linux to cloud, cloud to linux
        )) {
            assertEquals(
                "($source, $targetDirectory)", CopyKind.NETWORK,
                classifyCopy(source, targetDirectory)
            )
        }
    }

    // U-A04
    @Test
    fun skipAttributes_roundTrip() {
        val array = CopyOptions(
            false, false, false, true, 0, null, null, skipAttributes = true
        ).toArray()
        assertTrue(array.toList().toString(), SkipAttributesCopyOption in array)
        val copyOptions = array.toCopyOptions()
        assertTrue(copyOptions.skipAttributes)
        assertTrue(copyOptions.noFollowLinks)
        assertFalse(copyOptions.replaceExisting)
        assertFalse(copyOptions.copyAttributes)
        assertFalse(copyOptions.atomicMove)
    }

    // U-A05
    @Test
    fun skipAttributes_parsedWithStandardOptions() {
        val copyOptions = arrayOf(
            LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING,
            SkipAttributesCopyOption
        ).toCopyOptions()
        assertTrue(copyOptions.skipAttributes)
        assertTrue(copyOptions.replaceExisting)
        assertTrue(copyOptions.noFollowLinks)
    }

    // U-A06
    @Test
    fun skipAttributes_defaultFalse() {
        assertFalse(arrayOf(LinkOption.NOFOLLOW_LINKS).toCopyOptions().skipAttributes)
        val copyOptions = CopyOptions(true, true, false, true, 0, null, null)
        assertFalse(copyOptions.skipAttributes)
        val array = copyOptions.toArray()
        assertFalse(array.toList().toString(), SkipAttributesCopyOption in array)
        assertFalse(array.toCopyOptions().skipAttributes)
    }
}
