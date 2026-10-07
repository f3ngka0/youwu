package com.fengkao.timetable.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LicenseState(val loading: Boolean = false, val text: String = "", val error: String? = null)
internal class ProfileInfoViewModel(app: Application) : AndroidViewModel(app) {
    private val _licenses = MutableStateFlow(LicenseState())
    val licenses = _licenses.asStateFlow()
    fun loadLicenses() {
        if (_licenses.value.loading || _licenses.value.text.isNotBlank()) return
        _licenses.value = LicenseState(loading = true)
        viewModelScope.launch {
            _licenses.value = try {
                val text = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }
                LicenseState(text = text)
            } catch (_: java.io.IOException) { LicenseState(error = "开源许可读取失败，请重试") }
        }
    }
}
