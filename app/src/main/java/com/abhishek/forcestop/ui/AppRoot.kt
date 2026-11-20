package com.abhishek.forcestop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhishek.forcestop.ui.diagnostics.DiagnosticsScreen
import com.abhishek.forcestop.ui.home.HomeScreen
import com.abhishek.forcestop.ui.setup.SetupScreen

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    when (state.screen) {
        Screen.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Screen.SETUP -> SetupScreen(state, snackbar, viewModel)
        Screen.HOME -> HomeScreen(state, snackbar, viewModel)
        Screen.DIAGNOSTICS -> DiagnosticsScreen(state, viewModel)
    }
}
