package com.example.aimobileagent.ui.screen.appmanage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.domain.model.AppCapability
import com.example.aimobileagent.domain.repository.AppCapabilityRepository
import com.example.aimobileagent.domain.repository.InstalledAppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppManageUiState(
    val registeredApps: List<AppCapability> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isScanning: Boolean = false,
    val showAddDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class AppManageViewModel @Inject constructor(
    private val repository: AppCapabilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppManageUiState())
    val uiState: StateFlow<AppManageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { apps ->
                _uiState.update { it.copy(registeredApps = apps) }
            }
        }
    }

    /** 扫描手机上已安装的 App */
    fun scanInstalledApps() {
        _uiState.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            try {
                val apps = repository.getInstalledApps()
                _uiState.update { it.copy(installedApps = apps, isScanning = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isScanning = false, message = "扫描失败: ${e.message}")
                }
            }
        }
    }

    /** 添加指定 App 到能力目录 */
    fun addApp(pkg: String, name: String) {
        viewModelScope.launch {
            try {
                repository.add(
                    packageName = pkg,
                    appName = name,
                    supportedActions = listOf("open_app", "tap") // 默认支持基本操作
                )
                _uiState.update {
                    it.copy(
                        message = "已添加 $name",
                        installedApps = it.installedApps.filter { a -> a.packageName != pkg }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "添加失败: ${e.message}") }
            }
        }
    }

    /** 删除已注册的 App */
    fun removeApp(packageName: String, appName: String) {
        viewModelScope.launch {
            try {
                repository.remove(packageName)
                _uiState.update { it.copy(message = "已移除 $appName") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "移除失败: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
