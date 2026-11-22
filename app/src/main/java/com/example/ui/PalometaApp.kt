package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.parser.StorageExplorerHelper
import com.example.ui.components.EmptyIntakeScreen
import com.example.ui.components.ErrorWorkbenchScreen
import com.example.ui.components.LegalDisclaimerDialog
import com.example.ui.components.LoadingWorkbenchScreen
import com.example.ui.screens.DexExplorerScreen
import com.example.ui.screens.ElfInspectorScreen
import com.example.ui.screens.ExportStudioScreen
import com.example.ui.screens.ManifestHexScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.StorageExplorerOverlay
import com.example.ui.theme.DexScopeTheme
import com.example.ui.theme.MonoLabelStyle
import com.example.viewmodel.DexScopeViewModel
import com.example.viewmodel.ExportStatus
import com.example.viewmodel.WorkspaceState
import kotlinx.coroutines.launch
import java.io.File

const val ROUTE_OVERVIEW = "overview"
const val ROUTE_DEX = "dex"
const val ROUTE_ELF = "elf"
const val ROUTE_INSPECT = "inspect"
const val ROUTE_EXPORT = "export"

private class OpenApkFromDownloadsContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, StorageExplorerHelper.DOWNLOADS_ROOT_URI)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val WorkbenchDestinations = listOf(
    NavDestination(ROUTE_OVERVIEW, R.string.nav_overview, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_overview"),
    NavDestination(ROUTE_DEX, R.string.nav_dex, Icons.Filled.Code, Icons.Outlined.Code, "nav_dex"),
    NavDestination(ROUTE_ELF, R.string.nav_elf, Icons.Filled.Memory, Icons.Outlined.Memory, "nav_elf"),
    NavDestination(ROUTE_INSPECT, R.string.nav_inspect, Icons.Filled.Description, Icons.Outlined.Description, "nav_inspect"),
    NavDestination(ROUTE_EXPORT, R.string.nav_export, Icons.Filled.Archive, Icons.Outlined.Archive, "nav_export")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalometaApp(
    viewModel: DexScopeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }

    val openFromDownloadsLauncher = rememberLauncherForActivityResult(
        contract = OpenApkFromDownloadsContract()
    ) { uri ->
        if (uri != null) {
            viewModel.loadApkFromUri(uri)
        }
    }

    val openFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.mountStorageTree(treeUri)
        }
    }

    val createBundleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val out = context.contentResolver.openOutputStream(uri)
            if (out != null) {
                val safeName = "${uiState.exportConfig.projectName.ifBlank { "palometa" }}_ghidra_bundle.zip"
                viewModel.exportToDocumentStream(out, safeName)
            }
        }
    }

    LaunchedEffect(uiState.exportStatus) {
        val status = uiState.exportStatus
        if (status is ExportStatus.ReadyToShare) {
            val file = File(status.info.cachedZipPath)
            if (file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(context, authority, file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Palometa Ghidra Bundle: ${status.info.fileName}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Bundle to Workstation"))
            }
        }
    }

    val showCopiedToast: (String) -> Unit = { label ->
        scope.launch {
            snackbarHostState.showSnackbar("Copied $label to clipboard")
        }
    }

    DexScopeTheme(darkTheme = uiState.darkTheme) {
        val currentBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStack?.destination?.route ?: ROUTE_OVERVIEW
        val readyAnalysis = (uiState.workspace as? WorkspaceState.Ready)?.analysis

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1
                                )
                                if (readyAnalysis != null) {
                                    Text(
                                        text = readyAnalysis.manifest?.packageName ?: readyAnalysis.fileName,
                                        style = MonoLabelStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.openStorageExplorer(0) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_open_apk")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = stringResource(R.string.cd_open_apk)
                                )
                            }
                            IconButton(
                                onClick = { showLegalDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_legal_info")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.cd_legal_info)
                                )
                            }
                            IconButton(
                                onClick = viewModel::toggleTheme,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_toggle_theme")
                            ) {
                                Icon(
                                    imageVector = if (uiState.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = stringResource(R.string.cd_toggle_theme)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (readyAnalysis != null && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            WorkbenchDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_OVERVIEW) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (readyAnalysis != null && useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            WorkbenchDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_OVERVIEW) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (val ws = uiState.workspace) {
                            WorkspaceState.Empty -> {
                                EmptyIntakeScreen(
                                    onOpenApkClick = { viewModel.openStorageExplorer(0) },
                                    onBrowseInstalledClick = { viewModel.openStorageExplorer(1) },
                                    onInspectSelfClick = viewModel::loadSelfApk,
                                    onLegalClick = { showLegalDialog = true }
                                )
                            }

                            is WorkspaceState.Loading -> {
                                LoadingWorkbenchScreen(sourceLabel = ws.sourceLabel)
                            }

                            is WorkspaceState.Error -> {
                                ErrorWorkbenchScreen(
                                    message = ws.message,
                                    onOpenApkClick = { viewModel.openStorageExplorer(0) },
                                    onInspectSelfClick = viewModel::loadSelfApk
                                )
                            }

                            is WorkspaceState.Ready -> {
                                val analysis = ws.analysis
                                NavHost(
                                    navController = navController,
                                    startDestination = ROUTE_OVERVIEW
                                ) {
                                    composable(ROUTE_OVERVIEW) {
                                        OverviewScreen(
                                            analysis = analysis,
                                            onNavigateDex = { navController.navigate(ROUTE_DEX) },
                                            onNavigateElf = { navController.navigate(ROUTE_ELF) },
                                            onNavigateInspect = { navController.navigate(ROUTE_INSPECT) },
                                            onNavigateExport = { navController.navigate(ROUTE_EXPORT) },
                                            onOpenAnotherApk = { viewModel.openStorageExplorer(0) },
                                            onCopiedNotice = showCopiedToast
                                        )
                                    }
                                    composable(ROUTE_DEX) {
                                        DexExplorerScreen(
                                            analysis = analysis,
                                            selectedDexIndex = uiState.selectedDexIndex,
                                            searchQuery = uiState.dexSearchQuery,
                                            selectedClass = uiState.selectedClass,
                                            classSmaliSource = uiState.classSmaliSource,
                                            selectedMethod = uiState.selectedMethod,
                                            disassembledMethod = uiState.disassembledMethod,
                                            onSelectDex = viewModel::selectDexIndex,
                                            onSearchChange = viewModel::updateDexSearch,
                                            onSelectClass = viewModel::openDexClass,
                                            onSelectMethod = viewModel::openMethodDisassembly,
                                            onCloseMethod = viewModel::closeMethodDisassembly,
                                            onCopiedNotice = showCopiedToast
                                        )
                                    }
                                    composable(ROUTE_ELF) {
                                        ElfInspectorScreen(
                                            analysis = analysis,
                                            selectedElf = uiState.selectedElf,
                                            onSelectElf = viewModel::selectElf
                                        )
                                    }
                                    composable(ROUTE_INSPECT) {
                                        ManifestHexScreen(
                                            analysis = analysis,
                                            stringSearchQuery = uiState.stringSearchQuery,
                                            leaksOnlyFilter = uiState.leaksOnlyFilter,
                                            stringCategoryFilter = uiState.stringCategoryFilter,
                                            entrySearch = uiState.inspectEntrySearch,
                                            hexEntryPath = uiState.hexEntryPath,
                                            hexLines = uiState.hexLines,
                                            onStringSearchChange = viewModel::updateStringSearch,
                                            onToggleLeaksOnly = viewModel::toggleLeaksOnlyFilter,
                                            onSelectStringCategory = viewModel::selectStringCategoryFilter,
                                            onEntrySearchChange = viewModel::updateInspectSearch,
                                            onOpenHexEntry = viewModel::openHexViewer,
                                            onCloseHexEntry = viewModel::closeHexViewer,
                                            onCopiedNotice = showCopiedToast
                                        )
                                    }
                                    composable(ROUTE_EXPORT) {
                                        ExportStudioScreen(
                                            analysis = analysis,
                                            config = uiState.exportConfig,
                                            preview = uiState.exportPreview,
                                            exportStatus = uiState.exportStatus,
                                            onProjectNameChange = { name ->
                                                viewModel.updateExportConfig { it.copy(projectName = name) }
                                            },
                                            onToggleDex = { v ->
                                                viewModel.updateExportConfig { it.copy(includeDex = v) }
                                            },
                                            onToggleNative = { v ->
                                                viewModel.updateExportConfig { it.copy(includeNativeLibs = v) }
                                            },
                                            onToggleManifest = { v ->
                                                viewModel.updateExportConfig { it.copy(includeDecodedManifest = v) }
                                            },
                                            onToggleSigning = { v ->
                                                viewModel.updateExportConfig { it.copy(includeSigningMeta = v) }
                                            },
                                            onToggleScripts = { v ->
                                                viewModel.updateExportConfig { it.copy(includeGhidraScripts = v) }
                                            },
                                            onToggleAbi = viewModel::toggleAbiSelection,
                                            onSaveBundleClick = {
                                                val suggested = "${uiState.exportConfig.projectName.ifBlank { "palometa" }}_ghidra_bundle.zip"
                                                createBundleLauncher.launch(suggested)
                                            },
                                            onShareBundleClick = viewModel::prepareShareBundle,
                                            onCopiedNotice = showCopiedToast
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.storageExplorer.isOpen) {
                StorageExplorerOverlay(
                    state = uiState.storageExplorer,
                    onClose = viewModel::closeStorageExplorer,
                    onSelectTab = viewModel::selectStorageExplorerTab,
                    onDownloadsPickerClick = { openFromDownloadsLauncher.launch(Unit) },
                    onMountFolderClick = {
                        openFolderTreeLauncher.launch(StorageExplorerHelper.DOWNLOADS_DOCUMENT_URI)
                    },
                    onInspectSelfClick = viewModel::loadSelfApk,
                    onSelectDownloadItem = viewModel::loadApkFromDownload,
                    onSelectRecentUri = { uriStr ->
                        runCatching { Uri.parse(uriStr) }.getOrNull()?.let { viewModel.loadApkFromUri(it) }
                    },
                    onSelectInstalledApp = viewModel::loadApkFromInstalledApp,
                    onInstalledAppsQueryChange = viewModel::updateInstalledAppsQuery,
                    onToggleShowSystemApps = viewModel::toggleShowSystemApps,
                    onEnterDirectory = viewModel::enterStorageDirectory,
                    onSelectApkDocument = { item ->
                        runCatching { Uri.parse(item.documentUriString) }.getOrNull()?.let {
                            viewModel.loadApkFromUri(it)
                        }
                    },
                    onNavigateBreadcrumb = viewModel::navigateStorageBreadcrumb,
                    onToggleApkOnlyFilter = viewModel::toggleApkOnlyFilter
                )
            }

            if (showLegalDialog) {
                LegalDisclaimerDialog(onDismiss = { showLegalDialog = false })
            }
        }
    }
}
