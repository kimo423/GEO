package com.geo.ledger.ui.update

import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geo.ledger.BuildConfig
import com.geo.ledger.R
import com.geo.ledger.update.AppUpdateLaunch
import com.geo.ledger.update.AppUpdateViewModel
import com.geo.ledger.update.HttpUrlConnectionFetcher

@Composable
fun AppUpdateGate(
    viewModel: AppUpdateViewModel = viewModel(
        factory = AppUpdateViewModel.factory(BuildConfig.VERSION_CODE, HttpUrlConnectionFetcher()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.dialogVisible) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { viewModel.dismissForSession() },
        title = {
            Text(stringResource(R.string.update_available_title, state.remoteVersionName))
        },
        text = {
            Text(
                text = state.releaseNotes,
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = viewModel.apkUrlIfVisible()
                    viewModel.dismissForSession()
                    if (url != null) {
                        AppUpdateLaunch.open(url) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissForSession() }) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}


