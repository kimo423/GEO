package com.geo.ledger.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateUiState(
    val dialogVisible: Boolean = false,
    val remoteVersionName: String = "",
    val releaseNotes: String = "",
    val apkUrl: String = "",
)

class AppUpdateViewModel(
    private val localVersionCode: Int,
    private val fetcher: AppUpdateFetcher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val checkStarted = AtomicBoolean(false)
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    // Legacy manifest adapter retained for compatibility tests; construction never networks.
    // Production 1.2 uses SettingsToolsViewModel's explicit GitHub Release action.

    fun checkOnce() {
        if (!checkStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            val offer = try {
                val body = withContext(io) { fetcher.fetchManifest() }
                AppUpdatePolicy.parseAndValidate(body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (offer != null && AppUpdatePolicy.shouldPrompt(localVersionCode, offer.versionCode)) {
                _uiState.value = AppUpdateUiState(
                    dialogVisible = true,
                    remoteVersionName = offer.versionName,
                    releaseNotes = offer.releaseNotes,
                    apkUrl = offer.apkUrl,
                )
            }
        }
    }

    fun dismissForSession() {
        _uiState.value = _uiState.value.copy(dialogVisible = false)
    }

    fun apkUrlIfVisible(): String? =
        _uiState.value.takeIf { it.dialogVisible && it.apkUrl.isNotBlank() }?.apkUrl

    companion object {
        fun factory(localVersionCode: Int, fetcher: AppUpdateFetcher): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppUpdateViewModel(localVersionCode, fetcher) as T
            }
    }
}
