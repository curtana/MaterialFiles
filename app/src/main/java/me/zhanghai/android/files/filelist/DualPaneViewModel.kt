package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import me.zhanghai.android.files.util.valueCompat

class DualPaneViewModel : ViewModel() {
    private val _activePaneLiveData = MutableLiveData(0)
    val activePaneLiveData: LiveData<Int>
        get() = _activePaneLiveData
    val activePane: Int
        get() = _activePaneLiveData.valueCompat

    /**
     * Whether the second pane exists. This is not saved and is recomputed from the intent by the
     * host every time its view is created.
     */
    var isDualPaneEnabled = false

    fun setActivePane(index: Int) {
        // Invalid indices are ignored, because the layout may report a point outside the panes.
        if (index !in 0..1 || (!isDualPaneEnabled && index != 0)) {
            return
        }
        if (index == activePane) {
            return
        }
        _activePaneLiveData.value = index
    }

    fun togglePane() {
        if (!isDualPaneEnabled) {
            return
        }
        setActivePane(1 - activePane)
    }

    private val _paneModeLiveData = MutableLiveData(PaneMode(false, 0))
    val paneModeLiveData: LiveData<PaneMode>
        get() = _paneModeLiveData
    val paneMode: PaneMode
        get() = _paneModeLiveData.valueCompat

    fun setPaneMode(mode: PaneMode) {
        if (mode == paneMode) {
            return
        }
        _paneModeLiveData.value = mode
    }

    // Set by the host for the whole screen, and drawn by one of the panes.
    private val _fileJobIndicatorPhaseLiveData = MutableLiveData(FileJobIndicatorPhase.HIDDEN)
    val fileJobIndicatorPhaseLiveData: LiveData<FileJobIndicatorPhase>
        get() = _fileJobIndicatorPhaseLiveData
    val fileJobIndicatorPhase: FileJobIndicatorPhase
        get() = _fileJobIndicatorPhaseLiveData.valueCompat

    fun setFileJobIndicatorPhase(phase: FileJobIndicatorPhase) {
        if (phase == fileJobIndicatorPhase) {
            return
        }
        _fileJobIndicatorPhaseLiveData.value = phase
    }

    // The current path of each pane, published by the pane itself; null until it's known.
    private val _panePathLiveDatas = List(PANE_COUNT) { MutableLiveData<Path?>(null) }

    fun getPanePathLiveData(index: Int): LiveData<Path?> = _panePathLiveDatas[index]

    fun getPanePath(index: Int): Path? = _panePathLiveDatas[index].value

    fun setPanePath(index: Int, path: Path) {
        val liveData = _panePathLiveDatas[index]
        // The pane emits a new value even when it reloads the same path.
        if (path == liveData.value) {
            return
        }
        liveData.value = path
    }

    private val _isRequestingStorageAccessLiveData = MutableLiveData(false)
    var isStorageAccessRequested: Boolean
        get() = _isRequestingStorageAccessLiveData.valueCompat
        set(value) {
            _isRequestingStorageAccessLiveData.value = value
        }

    private val _isRequestingNotificationPermissionLiveData = MutableLiveData(false)
    var isNotificationPermissionRequested: Boolean
        get() = _isRequestingNotificationPermissionLiveData.valueCompat
        set(value) {
            _isRequestingNotificationPermissionLiveData.value = value
        }

    companion object {
        const val PANE_COUNT = 2
    }
}

internal fun otherPaneIndex(paneIndex: Int): Int = 1 - paneIndex

/**
 * The pane mode decided by [DualPaneLayout], where [paneWidthDp] is the width of a single pane for
 * the target state of the persistent drawer.
 */
data class PaneMode(val isDualPane: Boolean, val paneWidthDp: Int)
