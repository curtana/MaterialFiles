package me.zhanghai.android.files.filelist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zhanghai.android.files.util.valueCompat

class DualPaneViewModel : ViewModel() {
    private val _activePaneLiveData = MutableLiveData(0)
    val activePane: Int
        get() = _activePaneLiveData.valueCompat

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
