# Palometa — Agent & Developer Reference

> **Palometa** (Spanish for piranha) is a Kotlin + Jetpack Compose Android APK binary inspector, Smali/Dalvik decompiler, printable string scanner, and Ghidra desktop workstation export packager.
> Licensed under the **MIT License** (`LICENSE`).

---

## 1. Project Identity & Build Guardrails

| Property | Value | Notes |
| --- | --- | --- |
| App Name | `Palometa` | `res/values/strings.xml`, `settings.gradle.kts`, `metadata.json` |
| `namespace` | `com.example` | Keep unchanged (`R` class & Kotlin source paths) |
| `applicationId` | `com.palometa.application` | Final Play Store ID — do **not** alter |
| `versionCode` / `versionName` | `4` / `4.0` | Set in `app/build.gradle.kts` |
| SDK Levels | `minSdk = 24`, `compileSdk = 36` (`36.1`), `targetSdk = 36` | Compatible set — keep aligned |
| Unused SDKs | Firebase / Room / Retrofit / OkHttp / Moshi | Kept commented out in `app/build.gradle.kts` to minimize APK size |

### Non-Negotiable Constraints for Future Agents
1. **Zero-Permission Play Compliance**: Never add `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, or `REQUEST_INSTALL_PACKAGES` to `AndroidManifest.xml`.
2. **Gradle & Signing**: Never modify `signingConfigs` (`release`, `debugConfig`), `debug.keystore*`, or `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` in `metadata.json`. Never create `local.properties`.
3. **Adaptive Navigation & Back Handling**: `PalometaApp.kt` switches between `NavigationBar` (`< 600.dp`) and `NavigationRail` (`>= 600.dp`). All in-screen subviews (`DexClassDetailView`, `DalvikMethodDisassemblyView`, `HexInspectorView`, `StorageExplorerOverlay`) must preserve `BackHandler`.

---

## 2. Architecture & File Map

Pattern: **MVVM + Stateless/Hoisted Compose UI** (`DexScopeViewModel` holds `StateFlow<DexScopeUiState>` + active `LoadedApkSession`).

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host rendering PalometaApp()
├── model/
│   └── ApkAnalysis.kt               # Domain models, enums (StringFindingCategory), UI/export DTOs
├── parser/
│   ├── ApkArchiveParser.kt          # ZIP/PK validation, caching, v1/v2/v3 sig detection, hex reader
│   ├── StorageExplorerHelper.kt     # Downloads scanner (MediaStore + dir), SAF tree walker, recents
│   ├── DexParser.kt                 # DEX header, string/type/field/method pools, class/code parser
│   ├── DalvikDisassembler.kt        # Dalvik opcode + register/literal/symbol decoder
│   ├── SmaliGenerator.kt            # Class Smali emitter + SmaliSyntaxHighlighter
│   ├── ElfParser.kt                 # ELF32/ELF64 headers, sections, .dynsym/JNI, Ghidra LanguageID
│   ├── AxmlDecoder.kt               # Binary AndroidManifest.xml (AXML) decoder
│   ├── ApkStringScanner.kt          # Printable string extractor & leak/secret classifier
│   └── BinaryReader.kt              # Little/big-endian cursor & hash/byte formatters
├── export/
│   └── WorkstationExporter.kt       # Builds Ghidra .zip bundles + JSON/Python/Markdown helpers
├── viewmodel/
│   └── DexScopeViewModel.kt         # Coroutine orchestration, filter state, session lifecycle
└── ui/
    ├── PalometaApp.kt               # Navigation host, SAF launchers, FileProvider share sheet
    ├── components/
    │   └── WorkbenchComponents.kt   # EmptyIntakeScreen (piranha hero), cards, pills, loaders
    ├── screens/
    │   ├── StorageExplorerScreen.kt # Device & Downloads APK browser overlay
    │   ├── OverviewScreen.kt        # Summary, APK structure segments, hashes, quick actions
    │   ├── DexExplorerScreen.kt     # Multi-DEX chips, Classes, Smali Source, Methods, Dalvik view
    │   ├── ElfInspectorScreen.kt    # Native .so ABI/ELF header, sections, JNI exports
    │   ├── ManifestHexScreen.kt     # 3 tabs: Strings (leak filter), Manifest XML, Hex dump
    │   └── ExportStudioScreen.kt    # Bundle toggles, ABI filter, headless cmd preview, ZIP/Share
    └── theme/
        ├── Color.kt                 # Warm Cream (light) & Warm Dusk Plum (dark) tokens
        ├── Type.kt                  # Space Grotesk, Inter, JetBrains Mono type scale
        └── Theme.kt                 # DexScopeTheme + LocalDexScopeSyntax
```

---

## 3. Core Workflows

### A. APK Intake, Downloads & Installed Apps (Zero-Permission)
1. **Installed Applications Tab (`StorageExplorerHelper.scanInstalledApps`)**: Uses a targeted `<queries>` intent filter (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) in `AndroidManifest.xml`—avoiding the sensitive `QUERY_ALL_PACKAGES` permission—to list installed launchable apps with their real icon, package ID, version, split-APK count, and readable `base.apk` path (`publicSourceDir`/`sourceDir`). Users can search by name/package, toggle system apps, and tap any app to load its `base.apk` into the analyzer.
2. **Primary Picker (`OpenApkFromDownloadsContract`)**: Launches `Intent.ACTION_OPEN_DOCUMENT` with `type = "*/*"` and `DocumentsContract.EXTRA_INITIAL_URI = StorageExplorerHelper.DOWNLOADS_ROOT_URI`.
   - *Why `*/*`?* Android `DocumentsUI` often grays out `.apk` files in Downloads when restricted to `application/vnd.android.package-archive`. Instead, `ApkArchiveParser` validates the `PK\x03\x04` header after selection.
3. **In-App Downloads List (`StorageExplorerHelper.scanAccessibleDownloads`)**: Queries `MediaStore.Downloads.EXTERNAL_CONTENT_URI` (API 29+) and readable files in `Environment.DIRECTORY_DOWNLOADS`.
4. **SAF Folder Mounting (`OpenDocumentTree`)**: Opens at `StorageExplorerHelper.DOWNLOADS_DOCUMENT_URI` (note: Android 11+ blocks mounting the root `Download` folder itself, so users can mount a subfolder or SD root), persists URI permission, and browses via `DocumentsContract.buildChildDocumentsUriUsingTree`.
5. **Built-in Demo (`loadSelfApk`)**: Reads `context.applicationInfo.sourceDir` directly so users can test all features immediately without picking a file.

### B. DEX → Smali Decompilation & Method Disassembly
1. `ApkArchiveParser` locates all `classes*.dex` entries and builds `ParsedDexHandle` objects retained inside `LoadedApkSession`.
2. Selecting a class in `DexExplorerScreen` calls `DexScopeViewModel.openDexClass`, which triggers `SmaliGenerator.generateClassSmali(...)` on `Dispatchers.Default`.
3. `SmaliSyntaxHighlighter` renders directives (`.class`, `.method`), Dalvik opcodes, registers (`v0`, `p0`), type descriptors, string literals, and `#` comments in a horizontal + vertical scrollable `SelectionContainer`.
4. Clicking a method opens `DalvikMethodDisassemblyView` backed by `DalvikDisassembler.disassemble(...)`.

### C. Printable String & Leak Scanner
- `ApkStringScanner.scanAll(...)` aggregates printable strings from DEX pools, native `.so` binaries, decoded `AndroidManifest.xml`, and `.json`/`.xml`/`.properties`/`.txt`/`.pem` assets.
- Categorizes each finding into `StringFindingCategory`:
  - **Potential Leaks (`isPotentialLeak = true`)**: `URL_ENDPOINT`, `IP_ADDRESS`, `API_KEY_TOKEN`, `EMAIL`, `SECRET_HINT`
  - **Standard**: `FILE_PATH`, `PRINTABLE`
- Surfaced in `ManifestHexScreen` (`Strings` tab) with live search, a `"Leaks Only"` toggle, and category `FilterChip`s.

### D. Native ELF Inspection & Ghidra Workstation Export
- `ElfParser` extracts `ELF32`/`ELF64` headers, section flags, `.dynsym` / `JNI_OnLoad` / `Java_*` symbols, and maps architectures (`EM_AARCH64` → `AARCH64:LE:64:v8A`, `EM_ARM`, `EM_X86_64`, `EM_386`).
- `WorkstationExporter` packages a `.zip` bundle (saved via `CreateDocument` or shared via `FileProvider` from `cache/exports/`):
  - `dex/classes*.dex`
  - `native/<abi>/*.so` (filtered by selected ABIs)
  - `decoded/AndroidManifest.xml`
  - `meta/*` (`META-INF` signatures)
  - `ghidra_workspace/import_manifest.json`
  - `ghidra_workspace/ghidra_import_helper.py` (auto-bookmarks `JNI_OnLoad` & `Java_*` symbols)
  - `ghidra_workspace/README_ANALYSIS.md` (ready-to-run `analyzeHeadless` commands)

---

## 4. Visual Identity, Theme & Assets

- **Default Mode**: Light (`darkTheme = false` in `DexScopeUiState` and `DexScopeTheme`), with an editorial non-"hacker" aesthetic and a top-bar toggle to **Warm Dusk Plum** dark mode.
- **Palette Tokens (`Color.kt`)**:
  - Light surfaces: Warm Cream `#FBF6F3`, Ivory `#FFFFFF`, Blush `#F3E8E3`, Linen `#E0CFC8`
  - Accents: Muted Coral Red `#C45B56`, Tangerine `#D17A3E`, Ochre Yellow `#C59B38`, Dusty Pink `#B96482`, Subtle Plum `#855D8E`
  - Dark surfaces: Dusk Plum `#1D171C`, Cocoa `#271F25`, Mauve `#332830`
- **Fonts (`res/font/`)**: `space_grotesk.ttf`, `inter.ttf`, `jetbrains_mono.ttf`.
- **Hero Illustration**: `res/drawable/img_piranha_hero_1795067799828.jpg` (tiny piranhas nibbling a document).
- **App Icon**: `res/drawable/palometa_icon_art_1795108424199.jpg` in a 66dp adaptive foreground (`ic_launcher_foreground.xml`) on solid `#FAF1E8` (`ic_launcher_background.xml`), plus matching `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` & `ic_launcher_round.png`.

---

## 5. Build & Verification Commands

- **Compile APK (preferred verification)**: Use the `compile_applet` tool or `gradle assembleDebug` (never `./gradlew`).
- **Run Fast JVM Parser/Smali/Scanner Tests**:
  ```bash
  gradle :app:testDebugUnitTest --tests com.example.PalometaParserTest -Pkotlin.incremental=false
  ```
  - Covers `ElfParser`, `DalvikDisassembler`, `SmaliGenerator`, and `ApkStringScanner`.
  - Pass `-Pkotlin.incremental=false` to avoid Kotlin unit-test incremental cache issues if a prior run was interrupted.
- **Robolectric Test**: `ExampleRobolectricTest.kt` checks `app_name == "Palometa"`. Note: requires Robolectric runtime jars to be cached locally.
