package com.checklistboteco.presentation.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.checklistboteco.platform.LocationProvider
import com.checklistboteco.presentation.viewmodel.WorkClockViewModel

@Composable
actual fun WorkClockLocationEffect(viewModel: WorkClockViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionChanged(granted)
    }

    LaunchedEffect(Unit) {
        if (LocationProvider.hasPermission()) {
            viewModel.onLocationPermissionChanged(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationUpdates() }
    }
}
