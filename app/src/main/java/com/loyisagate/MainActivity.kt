package com.loyisagate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loyisagate.ui.components.AppSelectionDialog
import com.loyisagate.ui.screens.MainScreen
import com.loyisagate.ui.screens.SetupScreen
import com.loyisagate.ui.theme.LoyisaGateTheme
import com.loyisagate.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel?.checkNotificationPermission()
        }
    }

    private var viewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoyisaGateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vm: MainViewModel = viewModel()
                    viewModel = vm
                    val state by vm.state.collectAsState()

                    if (state.showAppSelection) {
                        AppSelectionDialog(
                            pm = packageManager,
                            selectedPackages = state.monitoredPackages,
                            onConfirm = { apps -> vm.saveSelectedApps(apps) },
                            onDismiss = { vm.hideAppSelection() }
                        )
                    }

                    if (state.isFirstLaunch || state.isEditing) {
                        SetupScreen(
                            initialUrl = state.webhookUrl,
                            initialEnabled = state.isEnabled,
                            selectedAppsCount = state.monitoredPackages.size,
                            onSelectApps = { vm.showAppSelection() },
                            onSave = { url, enabled ->
                                vm.saveSetup(url, enabled)
                            }
                        )
                    } else {
                        MainScreen(
                            webhookUrl = state.webhookUrl,
                            isEnabled = state.isEnabled,
                            notificationPermission = state.notificationPermissionGranted,
                            appsPermission = state.appsPermissionGranted,
                            backgroundPermission = state.backgroundPermissionGranted,
                            monitoredAppsCount = state.monitoredPackages.size,
                            setupStep = state.setupStep,
                            latestNotifications = state.latestNotifications,
                            onToggleEnabled = { vm.toggleEnabled(it) },
                            onRequestNotificationPermission = {
                                requestNotificationPermission(vm)
                            },
                            onSelectApps = { vm.showAppSelection() },
                            onRequestBackgroundPermission = {
                                vm.requestBackgroundPermission()
                            },
                            onEditSetup = {
                                vm.enterEditMode()
                            },
                            onResendNotification = { log ->
                                vm.resendNotification(log)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel?.checkNotificationPermission()
    }

    private fun requestNotificationPermission(vm: MainViewModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    vm.checkNotificationPermission()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            vm.requestNotificationPermission()
        }
    }
}
