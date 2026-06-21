package com.checklistboteco.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.checklistboteco.presentation.viewmodel.WorkClockViewModel

@Composable
actual fun WorkClockLocationEffect(viewModel: WorkClockViewModel) {
    DisposableEffect(viewModel) {
        viewModel.startLocationUpdates()
        onDispose { viewModel.stopLocationUpdates() }
    }
}
