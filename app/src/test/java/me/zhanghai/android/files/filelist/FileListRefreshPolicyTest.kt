package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Success
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FileListRefreshPolicyTest {
    private val files = listOf("a", "b")
    private val error = IOException("Permission denied")

    // R-B3-01 (REQ-29-01): T1-T3 with a list shown are silent, also with a failure keeping it.
    @Test
    fun shouldLoadSilently_automaticWithList_isSilent() {
        assertTrue(shouldLoadSilently(true, Success(files)))
        assertTrue(shouldLoadSilently(true, Success(emptyList<String>())))
        assertTrue(shouldLoadSilently(true, Failure(files, error)))
    }

    // R-B3-02 (REQ-29-03, 09): T1-T3 without a list show the loading state.
    @Test
    fun shouldLoadSilently_automaticWithoutList_showsLoading() {
        assertFalse(shouldLoadSilently(true, null))
        assertFalse(shouldLoadSilently(true, Loading(null)))
        assertFalse(shouldLoadSilently(true, Failure(null, error)))
    }

    // R-B3-03 (REQ-29-04, 05, 08): explicit loads always show the loading state.
    @Test
    fun shouldLoadSilently_explicit_showsLoading() {
        assertFalse(shouldLoadSilently(false, null))
        assertFalse(shouldLoadSilently(false, Success(files)))
        assertFalse(shouldLoadSilently(false, Failure(files, error)))
    }

    // R-B3-04 (REQ-29-06 (d)): a toast on the transition to failure.
    @Test
    fun shouldNotifyFailure_transitionToFailure_notifies() {
        assertTrue(shouldNotifyFailure(Success(files), Failure(files, error), false))
        assertTrue(shouldNotifyFailure(Loading(files), Failure(files, error), false))
        // A new view, e.g. after rotation.
        assertTrue(shouldNotifyFailure(null, Failure(files, error), false))
    }

    // R-B3-05 (REQ-29-06 (d)): no toast again for a silent load failing in failure state.
    @Test
    fun shouldNotifyFailure_failureAfterFailure_doesNotNotify() {
        assertFalse(
            shouldNotifyFailure(Failure(files, error), Failure(files, IOException("Again")), false)
        )
    }

    // R-B3-06 (REQ-29-06): a failure without files shows the error text instead.
    @Test
    fun shouldNotifyFailure_noFiles_doesNotNotify() {
        assertFalse(shouldNotifyFailure(Success(files), Failure(emptyList<String>(), error), true))
        assertFalse(shouldNotifyFailure(Success(files), Failure<List<String>>(null, error), true))
        assertFalse(shouldNotifyFailure(Loading(files), Success(files), true))
    }

    // R-B3-07 (REQ-29-07, HLD R6): an explicit refresh failing in failure state notifies, even if
    // the view never received its loading state because it was inactive (e.g. a hidden pane).
    @Test
    fun shouldNotifyFailure_explicitFailureInFailureState_notifies() {
        val previous = Failure(files, error)
        assertTrue(shouldNotifyFailure(previous, Failure(files, IOException("Again")), true))
    }

    // R-B3-08: the same state delivered again (e.g. a hidden pane shown again) doesn't notify again.
    @Test
    fun shouldNotifyFailure_sameStateAgain_doesNotNotify() {
        val failure = Failure(files, error)
        assertFalse(shouldNotifyFailure(failure, failure, true))
    }
}
