package com.example.parser

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.pm.PackageInfoCompat
import com.example.model.DownloadApkItem
import com.example.model.InstalledAppItem
import com.example.model.RecentApkItem
import com.example.model.StorageDocumentItem
import java.io.File

object StorageExplorerHelper {

    private const val PREFS_NAME = "palometa_storage_prefs"
    private const val KEY_TREE_URI = "mounted_tree_uri"
    private const val KEY_RECENT_APKS = "recent_apks"

    val DOWNLOADS_ROOT_URI: Uri =
        Uri.parse("content://com.android.providers.downloads.documents/root/downloads")

    val DOWNLOADS_DOCUMENT_URI: Uri =
        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")

    fun saveMountedTreeUri(context: Context, treeUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
    }

    fun getMountedTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun clearMountedTreeUri(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TREE_URI)
            .apply()
    }

    fun scanAccessibleDownloads(context: Context): List<DownloadApkItem> {
        val seenNames = HashSet<String>()
        val results = mutableListOf<DownloadApkItem>()

        // 1. Check MediaStore.Downloads (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE
                )
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        if (!name.endsWith(".apk", ignoreCase = true)) continue
                        val id = cursor.getLong(idCol)
                        val size = cursor.getLong(sizeCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        if (seenNames.add("$name:$size")) {
                            results.add(
                                DownloadApkItem(
                                    name = name,
                                    uriString = uri.toString(),
                                    filePath = null,
                                    sizeBytes = size
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Check standard Downloads directory if readable directly
        runCatching {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists() && downloadsDir.canRead()) {
                downloadsDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) && it.canRead() }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        val size = file.length()
                        if (seenNames.add("${file.name}:$size")) {
                            results.add(
                                DownloadApkItem(
                                    name = file.name,
                                    uriString = null,
                                    filePath = file.absolutePath,
                                    sizeBytes = size
                                )
                            )
                        }
                    }
            }
        }

        return results
    }

    fun listTreeChildren(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String? = null
    ): Pair<String, List<StorageDocumentItem>> {
        val docId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val items = mutableListOf<StorageDocumentItem>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "unnamed" else "unnamed"
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

                items.add(
                    StorageDocumentItem(
                        documentId = childId,
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMs = mod,
                        mimeType = mime,
                        documentUriString = docUri.toString()
                    )
                )
            }
        }

        val sorted = items.sortedWith(
            compareByDescending<StorageDocumentItem> { it.isDirectory }
                .thenByDescending { it.isApk }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        return docId to sorted
    }

    fun addRecentApk(context: Context, name: String, uri: Uri, sizeBytes: Long) {
        val current = getRecentApks(context).toMutableList()
        val uriStr = uri.toString()
        current.removeAll { it.uriString == uriStr }
        current.add(0, RecentApkItem(name = name, uriString = uriStr, sizeBytes = sizeBytes))
        val serialized = current.take(8).joinToString("\n") {
            "${it.name.replace("|", "_")}|${it.sizeBytes}|${it.uriString}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_APKS, serialized)
            .apply()
    }

    fun getRecentApks(context: Context): List<RecentApkItem> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_APKS, null) ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size == 3) {
                    RecentApkItem(
                        name = parts[0],
                        sizeBytes = parts[1].toLongOrNull() ?: 0L,
                        uriString = parts[2]
                    )
                } else null
            }
            .toList()
    }

    fun scanInstalledApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrElse { emptyList() }

        val seenPackages = HashSet<String>()
        val items = mutableListOf<InstalledAppItem>()

        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val appInfo = activityInfo.applicationInfo ?: continue
            val pkgName = appInfo.packageName ?: continue
            if (!seenPackages.add(pkgName)) continue

            val appName = runCatching { info.loadLabel(pm).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()
                ?: pkgName

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgName, 0)
                }
            }.getOrNull()

            val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: "?"
            val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

            val basePath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: ""
            val baseFile = if (basePath.isNotBlank()) File(basePath) else null
            val readable = baseFile != null && baseFile.exists() && baseFile.canRead()
            val baseSize = if (readable && baseFile != null) baseFile.length() else 0L
            val splitPaths = (appInfo.splitPublicSourceDirs ?: appInfo.splitSourceDirs)
                ?.filterNotNull()
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            items.add(
                InstalledAppItem(
                    appName = appName,
                    packageName = pkgName,
                    versionName = versionName,
                    versionCode = versionCode,
                    baseApkPath = basePath,
                    splitApkPaths = splitPaths,
                    apkSizeBytes = baseSize,
                    isSystemApp = isSystem,
                    canReadBaseApk = readable
                )
            )
        }

        return items.sortedWith(
            compareBy<InstalledAppItem> { it.isSystemApp }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
        )
    }
}
