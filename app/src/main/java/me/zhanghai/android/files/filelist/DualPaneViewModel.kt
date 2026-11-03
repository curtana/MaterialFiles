package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
}

/**
 * The pane mode decided by [DualPaneLayout], where [paneWidthDp] is the width of a single pane for
 * the target state of the persistent drawer.
 */
data class PaneMode(val isDualPane: Boolean, val paneWidthDp: Int)
