package com.example.model

enum class EntryCategory(val label: String) {
    DEX("DEX Bytecode"),
    NATIVE_SO("Native ELF (.so)"),
    MANIFEST("Manifest"),
    RESOURCES("Resources"),
    SIGNATURE("Signature"),
    ASSETS("Assets"),
    OTHER("Other")
}

data class ApkEntryInfo(
    val path: String,
    val category: EntryCategory,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32Hex: String,
    val compressionMethod: String
)

data class ManifestComponent(
    val type: String,
    val name: String,
    val exported: Boolean?
)

data class ManifestAnalysis(
    val decodedXml: String,
    val packageName: String,
    val versionCode: String,
    val versionName: String,
    val minSdk: String,
    val targetSdk: String,
    val debuggable: Boolean,
    val allowBackup: Boolean?,
    val usesCleartextTraffic: Boolean?,
    val permissions: List<String>,
    val components: List<ManifestComponent>
)

data class DalvikInstruction(
    val offsetBytes: Int,
    val offsetHex: String,
    val rawHex: String,
    val opcode: String,
    val operands: String,
    val comment: String = "",
    val smaliOperand: String = ""
)

data class DexFieldInfo(
    val name: String,
    val typeDescriptor: String,
    val accessFlags: String,
    val isStatic: Boolean
)

data class DexMethodInfo(
    val methodIndex: Int,
    val name: String,
    val prototype: String,
    val rawPrototype: String,
    val accessFlags: String,
    val isDirect: Boolean,
    val codeOffset: Int,
    val registersSize: Int,
    val insSize: Int,
    val outsSize: Int,
    val insnsSizeWords: Int
)

data class DexClassInfo(
    val index: Int,
    val descriptor: String,
    val displayName: String,
    val packageName: String,
    val superClass: String,
    val superDescriptor: String,
    val accessFlags: String,
    val sourceFile: String?,
    val staticFieldsCount: Int,
    val instanceFieldsCount: Int,
    val fields: List<DexFieldInfo>,
    val directMethods: List<DexMethodInfo>,
    val virtualMethods: List<DexMethodInfo>
) {
    val totalMethods: Int get() = directMethods.size + virtualMethods.size
}

enum class StringFindingCategory(val label: String, val isPotentialLeak: Boolean) {
    API_KEY_TOKEN("Key / Token", true),
    URL_ENDPOINT("URL / Endpoint", true),
    IP_ADDRESS("IPv4 Address", true),
    EMAIL("Email", true),
    SECRET_HINT("Hardcoded Secret", true),
    FILE_PATH("System / App Path", true),
    CRYPTO_JNI("Crypto / JNI", false),
    PRINTABLE("Printable String", false)
}

data class ApkPrintableString(
    val id: Int,
    val value: String,
    val sourceFile: String,
    val category: StringFindingCategory
)

data class NotableString(
    val index: Int,
    val category: String,
    val value: String
)

data class DexSummary(
    val entryName: String,
    val version: String,
    val fileSize: Int,
    val adler32Hex: String,
    val sha1Hex: String,
    val stringCount: Int,
    val typeCount: Int,
    val protoCount: Int,
    val fieldCount: Int,
    val methodCount: Int,
    val classDefCount: Int,
    val classes: List<DexClassInfo>,
    val notableStrings: List<NotableString>,
    val sampleStrings: List<String>
)

data class ElfSectionInfo(
    val index: Int,
    val name: String,
    val typeName: String,
    val flags: String,
    val addrHex: String,
    val offsetHex: String,
    val sizeBytes: Long
)

data class ElfSymbolInfo(
    val name: String,
    val binding: String,
    val type: String,
    val isExported: Boolean,
    val isJni: Boolean,
    val valueHex: String
)

data class ElfAnalysis(
    val entryPath: String,
    val fileName: String,
    val abi: String,
    val fileSize: Long,
    val elfClass: String,
    val endianness: String,
    val machineName: String,
    val ghidraLanguageId: String,
    val ghidraCompilerSpec: String,
    val elfType: String,
    val entryPointHex: String,
    val programHeadersCount: Int,
    val sections: List<ElfSectionInfo>,
    val dynamicSymbols: List<ElfSymbolInfo>,
    val jniExports: List<String>,
    val hasJniOnLoad: Boolean
)

data class ApkAnalysis(
    val fileName: String,
    val cachedApkPath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val md5: String,
    val signingSchemes: List<String>,
    val entries: List<ApkEntryInfo>,
    val manifest: ManifestAnalysis?,
    val dexFiles: List<DexSummary>,
    val elfFiles: List<ElfAnalysis>,
    val printableStrings: List<ApkPrintableString>,
    val analysisDurationMs: Long
) {
    val totalDexBytes: Long
        get() = entries.filter { it.category == EntryCategory.DEX }.sumOf { it.uncompressedSize }

    val totalNativeBytes: Long
        get() = entries.filter { it.category == EntryCategory.NATIVE_SO }.sumOf { it.uncompressedSize }

    val totalResourceBytes: Long
        get() = entries.filter {
            it.category == EntryCategory.RESOURCES || it.category == EntryCategory.MANIFEST
        }.sumOf { it.uncompressedSize }

    val totalOtherBytes: Long
        get() = (entries.sumOf { it.uncompressedSize } - totalDexBytes - totalNativeBytes - totalResourceBytes)
            .coerceAtLeast(0L)

    val availableAbis: List<String>
        get() = elfFiles.map { it.abi }.distinct().sorted()

    val leakFindingsCount: Int
        get() = printableStrings.count { it.category.isPotentialLeak }
}

data class HexDumpLine(
    val offsetHex: String,
    val hexBytes: String,
    val asciiText: String
)

data class StorageDocumentItem(
    val documentId: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val mimeType: String,
    val documentUriString: String
) {
    val isApk: Boolean
        get() = !isDirectory && (
            name.endsWith(".apk", ignoreCase = true) ||
                mimeType == "application/vnd.android.package-archive"
            )
}

data class RecentApkItem(
    val name: String,
    val uriString: String,
    val sizeBytes: Long
)

data class DownloadApkItem(
    val name: String,
    val uriString: String?,
    val filePath: String?,
    val sizeBytes: Long
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val baseApkPath: String,
    val splitApkPaths: List<String>,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val canReadBaseApk: Boolean
)

data class ExportConfig(
    val projectName: String,
    val includeDex: Boolean = true,
    val includeNativeLibs: Boolean = true,
    val includeDecodedManifest: Boolean = true,
    val includeSigningMeta: Boolean = true,
    val includeGhidraScripts: Boolean = true,
    val selectedAbis: Set<String> = emptySet()
)

data class ExportBundlePreview(
    val includedPaths: List<String>,
    val estimatedUncompressedBytes: Long,
    val importManifestJson: String,
    val ghidraPythonScript: String,
    val headlessCommand: String
)

data class ExportSuccessInfo(
    val fileName: String,
    val byteSize: Long,
    val sha256: String,
    val entryCount: Int,
    val cachedZipPath: String
)
