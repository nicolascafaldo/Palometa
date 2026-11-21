package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.DownloadApkItem
import com.example.model.InstalledAppItem
import com.example.model.StorageDocumentItem
import com.example.parser.BinaryFormatters
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.LocalDexScopeSyntax
import com.example.ui.theme.MonoLabelStyle
import com.example.viewmodel.StorageExplorerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageExplorerOverlay(
    state: StorageExplorerState,
    onClose: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onDownloadsPickerClick: () -> Unit,
    onMountFolderClick: () -> Unit,
    onInspectSelfClick: () -> Unit,
    onSelectDownloadItem: (DownloadApkItem) -> Unit,
    onSelectRecentUri: (String) -> Unit,
    onSelectInstalledApp: (InstalledAppItem) -> Unit,
    onInstalledAppsQueryChange: (String) -> Unit,
    onToggleShowSystemApps: () -> Unit,
    onEnterDirectory: (StorageDocumentItem) -> Unit,
    onSelectApkDocument: (StorageDocumentItem) -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onToggleApkOnlyFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (state.breadcrumbs.size > 1) {
            onNavigateBreadcrumb(state.breadcrumbs.lastIndex - 1)
        } else {
            onClose()
        }
    }

    val syntax = LocalDexScopeSyntax.current
    val displayedItems = remember(state.items, state.apkOnlyFilter) {
        if (state.apkOnlyFilter) {
            state.items.filter { it.isDirectory || it.isApk }
        } else {
            state.items
        }
    }
    val filteredInstalledApps = remember(
        state.installedApps,
        state.installedAppsQuery,
        state.showSystemApps
    ) {
        state.installedApps.filter { app ->
            val sysOk = state.showSystemApps || !app.isSystemApp
            val qOk = state.installedAppsQuery.isBlank() ||
                app.appName.contains(state.installedAppsQuery, ignoreCase = true) ||
                app.packageName.contains(state.installedAppsQuery, ignoreCase = true)
            sysOk && qOk
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("storage_explorer_close")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close file explorer")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APK & Installed Apps Browser",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Inspect installed apps or open .apk files from Downloads and storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PrimaryTabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { onSelectTab(0) },
                    text = { Text("APK Files") },
                    modifier = Modifier.testTag("explorer_tab_files")
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { onSelectTab(1) },
                    text = { Text("Installed Apps (${filteredInstalledApps.size})") },
                    modifier = Modifier.testTag("explorer_tab_installed")
                )
            }

            HorizontalDivider(color = syntax.borderSubtle)

            if (state.activeTab == 1) {
                InstalledAppsTabContent(
                    apps = filteredInstalledApps,
                    totalDiscoveredCount = state.installedApps.size,
                    isLoading = state.isLoadingInstalledApps,
                    query = state.installedAppsQuery,
                    showSystemApps = state.showSystemApps,
                    browserError = state.browserError,
                    onQueryChange = onInstalledAppsQueryChange,
                    onToggleShowSystemApps = onToggleShowSystemApps,
                    onSelectApp = onSelectInstalledApp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                item {
                    TerminalCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionLabel(
                                title = "Select APK File",
                                subtitle = "Use Pick from Downloads to choose any .apk in your Downloads folder"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onDownloadsPickerClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("explorer_downloads_picker_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick from Downloads")
                                }
                                OutlinedButton(
                                    onClick = onMountFolderClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("explorer_mount_folder_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CreateNewFolder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (state.mountedTreeUri == null) "Mount Folder" else "Change Folder")
                                }
                            }
                            Text(
                                text = "Note: Android restricts folder-tree mounting on the root Downloads folder itself — tap 'Pick from Downloads' above to select .apk files directly from Downloads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onInspectSelfClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("explorer_self_apk_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspect Built-in Demo APK (Palometa-self.apk)")
                            }
                        }
                    }
                }

                if (state.downloadsApks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Downloads (.apk)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            state.downloadsApks.forEach { item ->
                                TerminalCard(
                                    onClick = { onSelectDownloadItem(item) },
                                    modifier = Modifier.testTag("download_apk_${item.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = BinaryFormatters.formatBytes(item.sizeBytes),
                                                    style = MonoLabelStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        StatusPill(
                                            text = "APK",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.recentApks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Recent APKs",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            state.recentApks.forEach { recent ->
                                TerminalCard(
                                    onClick = { onSelectRecentUri(recent.uriString) },
                                    modifier = Modifier.testTag("recent_apk_${recent.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = recent.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = BinaryFormatters.formatBytes(recent.sizeBytes),
                                                    style = MonoLabelStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        StatusPill(
                                            text = "LOAD",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.browserError != null) {
                    item {
                        TerminalCard(modifier = Modifier.widthIn(max = 760.dp)) {
                            Text(
                                text = state.browserError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                if (state.mountedTreeUri != null) {
                    item {
                        Column(
                            modifier = Modifier.widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mounted Folder Browser",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                FilterChip(
                                    selected = state.apkOnlyFilter,
                                    onClick = onToggleApkOnlyFilter,
                                    label = { Text(".apk only", style = MonoLabelStyle) }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.breadcrumbs.forEachIndexed { idx, crumb ->
                                    TextButton(
                                        onClick = { onNavigateBreadcrumb(idx) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(
                                            text = crumb.label,
                                            style = MonoLabelStyle,
                                            fontWeight = if (idx == state.breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    if (idx < state.breadcrumbs.lastIndex) {
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.isBrowsing) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (displayedItems.isEmpty()) {
                        item {
                            TerminalCard(modifier = Modifier.widthIn(max = 760.dp)) {
                                Text(
                                    text = if (state.apkOnlyFilter) {
                                        "No folders or .apk files in this directory. Toggle '.apk only' to view all files."
                                    } else {
                                        "This directory is empty."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(displayedItems, key = { it.documentId }) { item ->
                            TerminalCard(
                                modifier = Modifier
                                    .widthIn(max = 760.dp)
                                    .testTag("explorer_item_${item.name}"),
                                onClick = when {
                                    item.isDirectory -> ({ onEnterDirectory(item) })
                                    item.isApk -> ({ onSelectApkDocument(item) })
                                    else -> null
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                item.isDirectory -> Icons.Filled.Folder
                                                item.isApk -> Icons.Filled.Android
                                                else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                item.isDirectory -> MaterialTheme.colorScheme.secondary
                                                item.isApk -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (item.isDirectory || item.isApk) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (item.isDirectory) {
                                                    "Directory"
                                                } else {
                                                    BinaryFormatters.formatBytes(item.sizeBytes)
                                                },
                                                style = MonoLabelStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (item.isApk) {
                                        StatusPill(
                                            text = "APK",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else if (item.isDirectory) {
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = "Open directory",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppsTabContent(
    apps: List<InstalledAppItem>,
    totalDiscoveredCount: Int,
    isLoading: Boolean,
    query: String,
    showSystemApps: Boolean,
    browserError: String?,
    onQueryChange: (String) -> Unit,
    onToggleShowSystemApps: () -> Unit,
    onSelectApp: (InstalledAppItem) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Filter by app name or package ID…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installed_apps_search")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showSystemApps,
                        onClick = onToggleShowSystemApps,
                        label = { Text("Include system apps") },
                        modifier = Modifier.testTag("installed_apps_system_filter")
                    )
                    Text(
                        text = "${apps.size} of $totalDiscoveredCount launchable apps",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Discovers launchable installed apps via targeted Android <queries> visibility without broad package permissions. Tapping an app loads its base.apk for Smali, string & ELF inspection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (browserError != null) {
            item {
                TerminalCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Text(
                        text = browserError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (apps.isEmpty()) {
            item {
                TerminalCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "No matching installed apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                val iconBitmap: ImageBitmap? = remember(app.packageName) {
                    runCatching {
                        context.packageManager
                            .getApplicationIcon(app.packageName)
                            .toBitmap(width = 96, height = 96)
                            .asImageBitmap()
                    }.getOrNull()
                }

                TerminalCard(
                    onClick = { onSelectApp(app) },
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .testTag("installed_app_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = "${app.appName} icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Android,
                                contentDescription = "${app.appName} icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val splitNote = if (app.splitApkPaths.isNotEmpty()) {
                                " · +${app.splitApkPaths.size} split"
                            } else {
                                ""
                            }
                            Text(
                                text = "v${app.versionName} · ${BinaryFormatters.formatBytes(app.apkSizeBytes)}$splitNote",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        StatusPill(
                            text = when {
                                !app.canReadBaseApk -> "LOCKED"
                                app.isSystemApp -> "SYS"
                                else -> "APP"
                            },
                            containerColor = when {
                                !app.canReadBaseApk -> MaterialTheme.colorScheme.errorContainer
                                app.isSystemApp -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = when {
                                !app.canReadBaseApk -> MaterialTheme.colorScheme.onErrorContainer
                                app.isSystemApp -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
