/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.switchMap
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.databinding.FileListHostFragmentBinding
import me.zhanghai.android.files.databinding.FileListHostFragmentIncludeBinding
import me.zhanghai.android.files.navigation.NavigationFragment
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.DrawerLayoutOnBackPressedCallback
import me.zhanghai.android.files.ui.PersistentDrawerLayout
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.checkSelfPermission
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels

/**
 * Hosts the screen-level parts of the file list (drawers, navigation, permission requests) and
 * the file list pane.
 */
class FileListHostFragment : Fragment(), FileListFragment.Listener, NavigationFragment.Listener,
    ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener {
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        RequestAllFilesAccessContract(), this::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
        this::onRequestStoragePermissionInSettingsResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestNotificationPermissionResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.POST_NOTIFICATIONS),
        this::onRequestNotificationPermissionInSettingsResult
    )

    private val args by args<Args>()

    private val viewModel by viewModels { { DualPaneViewModel() } }

    private lateinit var binding: Binding

    private lateinit var navigationFragment: NavigationFragment

    private lateinit var panes: List<FileListFragment>

    private val activePane: FileListFragment
        get() = panes[viewModel.activePane]

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        Binding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val isDualPaneEnabled = !isPickIntent(args.intent)
        viewModel.isDualPaneEnabled = isDualPaneEnabled
        val pane1: FileListFragment
        var pane2: FileListFragment?
        if (savedInstanceState == null) {
            pane1 = FileListFragment().putArgs(FileListFragment.Args(args.intent, 0))
            // The second pane starts at the default directory.
            pane2 = if (isDualPaneEnabled) {
                FileListFragment().putArgs(FileListFragment.Args(Intent(), 1))
            } else {
                null
            }
            navigationFragment = NavigationFragment()
            // The panes must be added before the navigation fragment, because the latter accesses
            // the active pane through its listener when it's created.
            childFragmentManager.commit {
                add(R.id.paneContainer, pane1)
                pane2?.let { add(R.id.pane2Container, it) }
                add(R.id.navigationFragment, navigationFragment)
            }
        } else {
            pane1 = childFragmentManager.findFragmentById(R.id.paneContainer) as FileListFragment
            pane2 = childFragmentManager.findFragmentById(R.id.pane2Container) as FileListFragment?
            if (isDualPaneEnabled && pane2 == null) {
                // State saved by an older version of the app has only one pane. This must be
                // executed now, because the following code accesses the second pane.
                val newPane2 = FileListFragment().putArgs(FileListFragment.Args(Intent(), 1))
                childFragmentManager.commitNow { add(R.id.pane2Container, newPane2) }
                pane2 = newPane2
            }
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as NavigationFragment
        }
        panes = listOfNotNull(pane1, pane2)
        navigationFragment.listener = this
        // The pane mode of the previous configuration doesn't apply to the new views, and the
        // layout reports the new one after its first measure.
        viewModel.setPaneMode(PaneMode(false, 0))

        binding.dualPaneLayout.isSwipeEnabled = isDualPaneEnabled
        binding.dualPaneLayout.persistentDrawerView =
            if (binding.persistentDrawerLayout != null) binding.navigationFragment else null
        binding.dualPaneLayout.isPersistentDrawerOpen =
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
        binding.dualPaneLayout.listener = dualPaneLayoutListener
        binding.dualPaneLayout.activePane = viewModel.activePane
        val viewLifecycleOwner = viewLifecycleOwner
        viewModel.activePaneLiveData.observe(viewLifecycleOwner) {
            binding.dualPaneLayout.activePane = it
        }

        // A callback is added to the activity dispatcher when its owner is started, and a child
        // fragment is always started after its parent, so the panes keep their callbacks in their
        // own dispatchers and we forward to the active pane here. This callback must be added
        // before the drawer callback so that closing the drawer takes precedence.
        val paneOnBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                activePane.paneOnBackPressedDispatcher.onBackPressed()
            }
        }
        viewModel.activePaneLiveData.switchMap { panes[it].backPressedEnabledLiveData }
            .observe(viewLifecycleOwner) { paneOnBackPressedCallback.isEnabled = it }
        addOnBackPressedCallback(paneOnBackPressedCallback)
        binding.drawerLayout?.let {
            addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it))
        }
        // The panes don't use a support action bar or the toolbar's own back callback (which would
        // also catch the search view of an inactive pane), so collapsing the search view of the
        // active pane is added last to take precedence over all the others.
        val searchViewOnBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                activePane.collapseSearchView()
            }
        }
        viewModel.activePaneLiveData.switchMap { panes[it].searchViewExpandedLiveData }
            .observe(viewLifecycleOwner) { searchViewOnBackPressedCallback.isEnabled = it }
        addOnBackPressedCallback(searchViewOnBackPressedCallback)

        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) {
                onPersistentDrawerOpenChanged(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (!this::panes.isInitialized) {
            return false
        }
        return activePane.onKeyShortcut(keyCode, event)
    }

    fun onMenuKeyEvent(event: KeyEvent): Boolean {
        if (!this::panes.isInitialized) {
            return false
        }
        return activePane.onMenuKeyEvent(event)
    }

    // Must be kept in sync with the pick actions in FileListFragment.onActivityCreated().
    private fun isPickIntent(intent: Intent): Boolean =
        when (intent.action) {
            Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE -> true
            else -> false
        }

    private val dualPaneLayoutListener = object : DualPaneLayout.Listener {
        override fun onPaneModeChanged(mode: PaneMode) {
            viewModel.setPaneMode(mode)
        }

        override fun onSwipeToTogglePane() {
            viewModel.togglePane()
        }

        override fun onPaneActivated(index: Int) {
            viewModel.setActivePane(index)
        }
    }

    private fun onPersistentDrawerOpenChanged(open: Boolean) {
        binding.persistentDrawerLayout?.let {
            if (open) {
                it.openDrawer(GravityCompat.START)
            } else {
                it.closeDrawer(GravityCompat.START)
            }
        }
        binding.dualPaneLayout.isPersistentDrawerOpen = open
    }

    private fun refreshPanes() {
        panes.forEach { it.refresh() }
    }

    override val hasPersistentDrawer: Boolean
        get() = binding.persistentDrawerLayout != null

    override val isPersistentDrawerOpen: Boolean
        get() = binding.persistentDrawerLayout?.isDrawerOpen(GravityCompat.START) == true

    override fun onNavigationIconClicked() {
        binding.drawerLayout?.openDrawer(GravityCompat.START)
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
            )
        }
    }

    override val currentPath: Path
        get() = activePane.currentPath

    override fun navigateTo(path: Path) {
        activePane.navigateTo(path)
    }

    override fun navigateToRoot(path: Path) {
        activePane.navigateToRoot(path)
    }

    override fun navigateToDefaultRoot() {
        activePane.navigateToDefaultRoot()
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        // Follows the active pane, so that the navigation drawer shows the path of the new active
        // pane when it changes.
        viewModel.activePaneLiveData.switchMap { panes[it].currentPathLiveData }
            .observe(owner, observer)
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    private fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) {
            return
        }
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                ShowRequestAllFilesAccessRationaleDialogFragment.show(this)
                viewModel.isStorageAccessRequested = true
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )) {
                    ShowRequestStoragePermissionRationaleDialogFragment.show(this)
                } else {
                    requestStoragePermission()
                }
                viewModel.isStorageAccessRequested = true
            }
        }
    }

    override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccess()
        } else {
            viewModel.isStorageAccessRequested = false
            // This isn't an onActivityResult() callback so it's not delivered before calling
            // onResume(), and we need to do this manually.
            ensureNotificationPermission()
        }
    }

    private fun requestAllFilesAccess() {
        requestAllFilesAccessLauncher.launch(Unit)
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refreshPanes()
        }
    }

    override fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermission()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermission() {
        requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            refreshPanes()
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )) {
            ShowRequestStoragePermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestStoragePermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    override fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettings()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermissionInSettings() {
        requestStoragePermissionInSettingsLauncher.launch(Unit)
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refreshPanes()
        }
    }

    private fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )) {
                    ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
                } else {
                    requestNotificationPermission()
                }
                viewModel.isNotificationPermissionRequested = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermission()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.POST_NOTIFICATIONS
        )) {
            ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionInSettingsRationaleResult(
        shouldRequest: Boolean
    ) {
        if (shouldRequest) {
            requestNotificationPermissionInSettings()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissionInSettings() {
        requestNotificationPermissionInSettingsLauncher.launch(Unit)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(private val permissionName: String)
        : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) ==
                PackageManager.PERMISSION_GRANTED
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class Binding private constructor(
        val root: View,
        val drawerLayout: DrawerLayout? = null,
        val persistentDrawerLayout: PersistentDrawerLayout? = null,
        val dualPaneLayout: DualPaneLayout,
        val navigationFragment: View
    ) {
        companion object {
            fun inflate(
                inflater: LayoutInflater,
                root: ViewGroup?,
                attachToRoot: Boolean
            ): Binding {
                val binding = FileListHostFragmentBinding.inflate(inflater, root, attachToRoot)
                val bindingRoot = binding.root
                val includeBinding = FileListHostFragmentIncludeBinding.bind(bindingRoot)
                return Binding(
                    bindingRoot, includeBinding.drawerLayout, includeBinding.persistentDrawerLayout,
                    includeBinding.dualPaneLayout, includeBinding.navigationFragment
                )
            }
        }
    }
}
