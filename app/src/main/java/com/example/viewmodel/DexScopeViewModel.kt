package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.export.WorkstationExporter
import com.example.model.ApkAnalysis
import com.example.model.DalvikInstruction
import com.example.model.DexClassInfo
import com.example.model.DexMethodInfo
import com.example.model.DownloadApkItem
import com.example.model.ElfAnalysis
import com.example.model.ExportBundlePreview
import com.example.model.ExportConfig
import com.example.model.ExportSuccessInfo
import com.example.model.HexDumpLine
import com.example.model.InstalledAppItem
import com.example.model.RecentApkItem
import com.example.model.StorageDocumentItem
import com.example.model.StringFindingCategory
import com.example.parser.ApkArchiveParser
import com.example.parser.LoadedApkSession
import com.example.parser.StorageExplorerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

sealed interface WorkspaceState {
    data object Empty : WorkspaceState
    data class Loading(val sourceLabel: String) : WorkspaceState
    data class Ready(val analysis: ApkAnalysis) : WorkspaceState
    data class Error(val message: String) : WorkspaceState
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Exporting : ExportStatus
    data class ReadyToShare(val info: ExportSuccessInfo) : ExportStatus
    data class SavedToDocument(val info: ExportSuccessInfo) : ExportStatus
    data class Failed(val message: String) : ExportStatus
}

data class FolderBreadcrumb(
    val documentId: String,
    val label: String
)

data class StorageExplorerState(
    val isOpen: Boolean = false,
    val activeTab: Int = 0,
    val mountedTreeUri: Uri? = null,
    val breadcrumbs: List<FolderBreadcrumb> = emptyList(),
    val items: List<StorageDocumentItem> = emptyList(),
    val isBrowsing: Boolean = false,
    val apkOnlyFilter: Boolean = true,
    val downloadsApks: List<DownloadApkItem> = emptyList(),
    val recentApks: List<RecentApkItem> = emptyList(),
    val installedApps: List<InstalledAppItem> = emptyList(),
    val isLoadingInstalledApps: Boolean = false,
    val installedAppsQuery: String = "",
    val showSystemApps: Boolean = false,
    val browserError: String? = null
)

data class DexScopeUiState(
    val darkTheme: Boolean = false,
    val workspace: WorkspaceState = WorkspaceState.Empty,
    val storageExplorer: StorageExplorerState = StorageExplorerState(),
    val selectedDexIndex: Int = 0,
    val dexSearchQuery: String = "",
    val selectedClass: DexClassInfo? = null,
    val classSmaliSource: String = "",
    val selectedMethod: DexMethodInfo? = null,
    val disassembledMethod: List<DalvikInstruction> = emptyList(),
    val selectedElf: ElfAnalysis? = null,
    val stringSearchQuery: String = "",
    val leaksOnlyFilter: Boolean = false,
    val stringCategoryFilter: StringFindingCategory? = null,
    val inspectEntrySearch: String = "",
    val hexEntryPath: String? = null,
    val hexLines: List<HexDumpLine> = emptyList(),
    val exportConfig: ExportConfig = ExportConfig(projectName = "palometa_workspace"),
    val exportPreview: ExportBundlePreview? = null,
    val exportStatus: ExportStatus = ExportStatus.Idle
)

class DexScopeViewModel(application: Application) : AndroidViewModel(application) {

    private var currentSession: LoadedApkSession? = null

    private val _uiState = MutableStateFlow(
        DexScopeUiState(
            storageExplorer = StorageExplorerState(
                mountedTreeUri = StorageExplorerHelper.getMountedTreeUri(application),
                recentApks = StorageExplorerHelper.getRecentApks(application)
            )
        )
    )
    val uiState: StateFlow<DexScopeUiState> = _uiState.asStateFlow()

    fun toggleTheme() {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
    }

    fun openStorageExplorer(initialTab: Int = 0) {
        val ctx = getApplication<Application>()
        val treeUri = StorageExplorerHelper.getMountedTreeUri(ctx)
        val recents = StorageExplorerHelper.getRecentApks(ctx)
        _uiState.update {
            it.copy(
                storageExplorer = it.storageExplorer.copy(
                    isOpen = true,
                    activeTab = initialTab,
                    mountedTreeUri = treeUri,
                    recentApks = recents,
                    isLoadingInstalledApps = true,
                    browserError = null
                )
            )
        }
        viewModelScope.launch {
            val downloads = withContext(Dispatchers.IO) {
                StorageExplorerHelper.scanAccessibleDownloads(ctx)
            }
            _uiState.update {
                it.copy(storageExplorer = it.storageExplorer.copy(downloadsApks = downloads))
            }
        }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                StorageExplorerHelper.scanInstalledApps(ctx)
            }
            _uiState.update {
                it.copy(
                    storageExplorer = it.storageExplorer.copy(
                        installedApps = apps,
                        isLoadingInstalledApps = false
                    )
                )
            }
        }
        if (treeUri != null) {
            browseFolder(treeUri, parentDocumentId = null, folderLabel = "Mounted Folder", resetTrail = true)
        }
    }

    fun selectStorageExplorerTab(tab: Int) {
        _uiState.update {
            it.copy(storageExplorer = it.storageExplorer.copy(activeTab = tab))
        }
    }

    fun updateInstalledAppsQuery(query: String) {
        _uiState.update {
            it.copy(storageExplorer = it.storageExplorer.copy(installedAppsQuery = query))
        }
    }

    fun toggleShowSystemApps() {
        _uiState.update {
            it.copy(
                storageExplorer = it.storageExplorer.copy(
                    showSystemApps = !it.storageExplorer.showSystemApps
                )
            )
        }
    }

    fun loadApkFromInstalledApp(item: InstalledAppItem) {
        if (!item.canReadBaseApk || item.baseApkPath.isBlank()) {
            _uiState.update {
                it.copy(
                    storageExplorer = it.storageExplorer.copy(
                        browserError = "Android prevents reading ${item.appName}'s base.apk directly from storage."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val label = "${item.packageName}.apk"
            _uiState.update {
                it.copy(
                    workspace = WorkspaceState.Loading("${item.appName} ($label)"),
                    storageExplorer = it.storageExplorer.copy(isOpen = false),
                    selectedClass = null,
                    classSmaliSource = "",
                    selectedMethod = null,
                    selectedElf = null,
                    hexEntryPath = null,
                    exportStatus = ExportStatus.Idle
                )
            }
            try {
                val session = withContext(Dispatchers.IO) {
                    ApkArchiveParser.parseFromFilePath(getApplication(), item.baseApkPath, label)
                }
                applySession(session)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        workspace = WorkspaceState.Error(
                            t.message ?: "Failed to read installed application APK."
                        )
                    )
                }
            }
        }
    }

    fun loadApkFromDownload(item: DownloadApkItem) {
        if (item.uriString != null) {
            runCatching { Uri.parse(item.uriString) }.getOrNull()?.let {
                loadApkFromUri(it)
                return
            }
        }
        val path = item.filePath ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workspace = WorkspaceState.Loading(item.name),
                    storageExplorer = it.storageExplorer.copy(isOpen = false),
                    selectedClass = null,
                    classSmaliSource = "",
                    selectedMethod = null,
                    selectedElf = null,
                    hexEntryPath = null,
                    exportStatus = ExportStatus.Idle
                )
            }
            try {
                val session = withContext(Dispatchers.IO) {
                    ApkArchiveParser.parseFromFilePath(getApplication(), path, item.name)
                }
                applySession(session)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(workspace = WorkspaceState.Error(t.message ?: "Failed to parse APK from Downloads."))
                }
            }
        }
    }

    fun closeStorageExplorer() {
        _uiState.update {
            it.copy(storageExplorer = it.storageExplorer.copy(isOpen = false))
        }
    }

    fun mountStorageTree(treeUri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        StorageExplorerHelper.saveMountedTreeUri(ctx, treeUri)
        val label = treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { "Storage" } ?: "Storage"
        browseFolder(treeUri, parentDocumentId = null, folderLabel = label, resetTrail = true)
    }

    fun enterStorageDirectory(item: StorageDocumentItem) {
        val treeUri = _uiState.value.storageExplorer.mountedTreeUri ?: return
        browseFolder(treeUri, parentDocumentId = item.documentId, folderLabel = item.name, resetTrail = false)
    }

    fun navigateStorageBreadcrumb(index: Int) {
        val explorer = _uiState.value.storageExplorer
        val treeUri = explorer.mountedTreeUri ?: return
        val target = explorer.breadcrumbs.getOrNull(index) ?: return
        val newTrail = explorer.breadcrumbs.take(index + 1)
        viewModelScope.launch {
            _uiState.update {
                it.copy(storageExplorer = it.storageExplorer.copy(isBrowsing = true, browserError = null))
            }
            try {
                val (_, children) = withContext(Dispatchers.IO) {
                    StorageExplorerHelper.listTreeChildren(getApplication(), treeUri, target.documentId)
                }
                _uiState.update {
                    it.copy(
                        storageExplorer = it.storageExplorer.copy(
                            breadcrumbs = newTrail,
                            items = children,
                            isBrowsing = false
                        )
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        storageExplorer = it.storageExplorer.copy(
                            isBrowsing = false,
                            browserError = t.message ?: "Unable to read folder."
                        )
                    )
                }
            }
        }
    }

    fun toggleApkOnlyFilter() {
        _uiState.update {
            it.copy(
                storageExplorer = it.storageExplorer.copy(
                    apkOnlyFilter = !it.storageExplorer.apkOnlyFilter
                )
            )
        }
    }

    private fun browseFolder(
        treeUri: Uri,
        parentDocumentId: String?,
        folderLabel: String,
        resetTrail: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    storageExplorer = it.storageExplorer.copy(
                        mountedTreeUri = treeUri,
                        isBrowsing = true,
                        browserError = null
                    )
                )
            }
            try {
                val (docId, children) = withContext(Dispatchers.IO) {
                    StorageExplorerHelper.listTreeChildren(getApplication(), treeUri, parentDocumentId)
                }
                _uiState.update { state ->
                    val trail = if (resetTrail) {
                        listOf(FolderBreadcrumb(docId, folderLabel))
                    } else {
                        state.storageExplorer.breadcrumbs + FolderBreadcrumb(docId, folderLabel)
                    }
                    state.copy(
                        storageExplorer = state.storageExplorer.copy(
                            mountedTreeUri = treeUri,
                            breadcrumbs = trail,
                            items = children,
                            isBrowsing = false
                        )
                    )
                }
            } catch (t: Throwable) {
                StorageExplorerHelper.clearMountedTreeUri(getApplication())
                _uiState.update {
                    it.copy(
                        storageExplorer = it.storageExplorer.copy(
                            mountedTreeUri = null,
                            breadcrumbs = emptyList(),
                            items = emptyList(),
                            isBrowsing = false,
                            browserError = "Folder access expired or unavailable. Please mount a folder again."
                        )
                    )
                }
            }
        }
    }

    fun loadApkFromUri(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workspace = WorkspaceState.Loading("Selected APK"),
                    storageExplorer = it.storageExplorer.copy(isOpen = false),
                    selectedClass = null,
                    classSmaliSource = "",
                    selectedMethod = null,
                    selectedElf = null,
                    hexEntryPath = null,
                    exportStatus = ExportStatus.Idle
                )
            }
            try {
                val session = withContext(Dispatchers.IO) {
                    ApkArchiveParser.parseFromUri(ctx, uri)
                }
                StorageExplorerHelper.addRecentApk(
                    ctx,
                    session.analysis.fileName,
                    uri,
                    session.analysis.fileSizeBytes
                )
                applySession(session)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(workspace = WorkspaceState.Error(t.message ?: "Failed to parse APK."))
                }
            }
        }
    }

    fun loadSelfApk() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workspace = WorkspaceState.Loading("Palometa-self.apk"),
                    storageExplorer = it.storageExplorer.copy(isOpen = false),
                    selectedClass = null,
                    classSmaliSource = "",
                    selectedMethod = null,
                    selectedElf = null,
                    hexEntryPath = null,
                    exportStatus = ExportStatus.Idle
                )
            }
            try {
                val session = withContext(Dispatchers.IO) {
                    ApkArchiveParser.parseSelfApk(getApplication())
                }
                applySession(session)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(workspace = WorkspaceState.Error(t.message ?: "Failed to inspect self APK."))
                }
            }
        }
    }

    private fun applySession(session: LoadedApkSession) {
        currentSession = session
        val analysis = session.analysis
        val defaultProj = (analysis.manifest?.packageName ?: analysis.fileName.substringBeforeLast('.'))
            .replace('.', '_')
        val initialConfig = ExportConfig(
            projectName = defaultProj,
            includeDex = analysis.dexFiles.isNotEmpty(),
            includeNativeLibs = analysis.elfFiles.isNotEmpty(),
            includeDecodedManifest = analysis.manifest != null,
            includeSigningMeta = true,
            includeGhidraScripts = true,
            selectedAbis = analysis.availableAbis.toSet()
        )
        val preview = WorkstationExporter.buildPreview(analysis, initialConfig)
        val recents = StorageExplorerHelper.getRecentApks(getApplication())
        _uiState.update {
            it.copy(
                workspace = WorkspaceState.Ready(analysis),
                storageExplorer = it.storageExplorer.copy(recentApks = recents),
                selectedDexIndex = 0,
                dexSearchQuery = "",
                selectedClass = null,
                classSmaliSource = "",
                selectedMethod = null,
                disassembledMethod = emptyList(),
                selectedElf = null,
                stringSearchQuery = "",
                leaksOnlyFilter = false,
                stringCategoryFilter = null,
                inspectEntrySearch = "",
                hexEntryPath = null,
                hexLines = emptyList(),
                exportConfig = initialConfig,
                exportPreview = preview,
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun selectDexIndex(index: Int) {
        _uiState.update {
            it.copy(
                selectedDexIndex = index,
                selectedClass = null,
                classSmaliSource = "",
                selectedMethod = null,
                disassembledMethod = emptyList()
            )
        }
    }

    fun updateDexSearch(query: String) {
        _uiState.update { it.copy(dexSearchQuery = query) }
    }

    fun openDexClass(dexEntryName: String, cls: DexClassInfo?) {
        if (cls == null) {
            _uiState.update {
                it.copy(
                    selectedClass = null,
                    classSmaliSource = "",
                    selectedMethod = null,
                    disassembledMethod = emptyList()
                )
            }
            return
        }
        val session = currentSession
        viewModelScope.launch {
            val smali = withContext(Dispatchers.Default) {
                session?.generateClassSmali(dexEntryName, cls) ?: ""
            }
            _uiState.update {
                it.copy(
                    selectedClass = cls,
                    classSmaliSource = smali,
                    selectedMethod = null,
                    disassembledMethod = emptyList()
                )
            }
        }
    }

    fun openMethodDisassembly(dexEntryName: String, method: DexMethodInfo) {
        val session = currentSession ?: return
        viewModelScope.launch {
            val insns = withContext(Dispatchers.Default) {
                session.disassembleMethod(dexEntryName, method)
            }
            _uiState.update {
                it.copy(
                    selectedMethod = method,
                    disassembledMethod = insns
                )
            }
        }
    }

    fun closeMethodDisassembly() {
        _uiState.update {
            it.copy(selectedMethod = null, disassembledMethod = emptyList())
        }
    }

    fun selectElf(elf: ElfAnalysis?) {
        _uiState.update { it.copy(selectedElf = elf) }
    }

    fun updateStringSearch(query: String) {
        _uiState.update { it.copy(stringSearchQuery = query) }
    }

    fun toggleLeaksOnlyFilter() {
        _uiState.update { it.copy(leaksOnlyFilter = !it.leaksOnlyFilter) }
    }

    fun selectStringCategoryFilter(category: StringFindingCategory?) {
        _uiState.update {
            val next = if (it.stringCategoryFilter == category) null else category
            it.copy(stringCategoryFilter = next)
        }
    }

    fun updateInspectSearch(query: String) {
        _uiState.update { it.copy(inspectEntrySearch = query) }
    }

    fun openHexViewer(entryPath: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) {
                session.readEntryHexDump(entryPath, maxBytes = 8192)
            }
            _uiState.update {
                it.copy(hexEntryPath = entryPath, hexLines = lines)
            }
        }
    }

    fun closeHexViewer() {
        _uiState.update { it.copy(hexEntryPath = null, hexLines = emptyList()) }
    }

    fun updateExportConfig(transform: (ExportConfig) -> ExportConfig) {
        val analysis = (uiState.value.workspace as? WorkspaceState.Ready)?.analysis ?: return
        val nextConfig = transform(uiState.value.exportConfig)
        val preview = WorkstationExporter.buildPreview(analysis, nextConfig)
        _uiState.update {
            it.copy(
                exportConfig = nextConfig,
                exportPreview = preview,
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun toggleAbiSelection(abi: String) {
        updateExportConfig { cfg ->
            val updated = cfg.selectedAbis.toMutableSet()
            if (abi in updated) updated.remove(abi) else updated.add(abi)
            cfg.copy(selectedAbis = updated)
        }
    }

    fun prepareShareBundle() {
        val analysis = (uiState.value.workspace as? WorkspaceState.Ready)?.analysis ?: return
        val config = uiState.value.exportConfig
        viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = ExportStatus.Exporting) }
            try {
                val result = withContext(Dispatchers.IO) {
                    WorkstationExporter.exportToCache(getApplication(), analysis, config)
                }
                _uiState.update { it.copy(exportStatus = ExportStatus.ReadyToShare(result)) }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(exportStatus = ExportStatus.Failed(t.message ?: "Export failed."))
                }
            }
        }
    }

    fun exportToDocumentStream(outputStream: OutputStream, fileName: String) {
        val analysis = (uiState.value.workspace as? WorkspaceState.Ready)?.analysis ?: return
        val config = uiState.value.exportConfig
        viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = ExportStatus.Exporting) }
            try {
                val result = withContext(Dispatchers.IO) {
                    outputStream.use { out ->
                        WorkstationExporter.exportToStream(analysis, config, out, fileName)
                    }
                }
                _uiState.update { it.copy(exportStatus = ExportStatus.SavedToDocument(result)) }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(exportStatus = ExportStatus.Failed(t.message ?: "Failed to save ZIP bundle."))
                }
            }
        }
    }
}
