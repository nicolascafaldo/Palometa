# AGENTS.md — Palometa

Android app: APK binary inspector (DEX/Dalvik, ELF, decoded manifest) with Ghidra
export bundling. One Gradle module (`:app`), Jetpack Compose + Material3.
No README, no CI (`.github/` absent), no ktlint/detekt/spotless/`.editorconfig`.
`metadata.json` is an AI Studio app descriptor — nothing in the build reads it.

## Architecture

- Entry: `app/src/main/java/com/example/MainActivity.kt` → `ui/PalometaApp.kt`
  (all NavHost routes `overview|dex|elf|inspect|export`, plus the SAF launchers)
  → `viewmodel/DexScopeViewModel.kt` (single `AndroidViewModel` owning **all**
  app state). Screens in `ui/screens/` just consume `uiState: StateFlow` — put
  new state/logic in the ViewModel, not in screens.
- `parser/` is pure Kotlin with no Android imports and is what the tests cover:
  `DexParser`, `ElfParser`, `AxmlDecoder`, `DalvikDisassembler` (also declares
  `DexSymbolResolver`), `SmaliGenerator`, `ApkStringScanner`, `ApkArchiveParser`,
  `BinaryReader`, `StorageExplorerHelper`.
- `export/WorkstationExporter.kt` builds the Ghidra zip (`dex/`, `native/`,
  `decoded/`, `meta/`, `ghidra_workspace/`).
- Naming is inconsistent: package/namespace `com.example`,
  applicationId `com.palometa.application`, theme `Theme.DexScope`, product
  "Palometa". Grep for `dexscope`/`com.example`, not just "Palometa". Do not
  rename the package — manifest, applicationId, and tests all depend on it.

## Build & verify

Nothing is on PATH — use absolute paths. Verified working build command:

```sh
JAVA_HOME=/home/anon/tools/jdk-21.0.13.1+2 \
ANDROID_HOME=/home/anon/android-sdk \
/home/anon/tools/gradle-9.3.1/bin/gradle :app:assembleDebug
```

- **`gradlew` is not committed** (only `gradle/wrapper/gradle-wrapper.properties`,
  Gradle 9.3.1). Gradle 9.3.1 lives at `/home/anon/tools/gradle-9.3.1/bin/gradle`.
- **The system `java` is a JRE without `javac`** — builds fail with
  "does not provide the required capabilities: [JAVA_COMPILER]" unless
  `JAVA_HOME` points at a full JDK (Temurin 21 at `/home/anon/tools/jdk-21.0.13.1+2`;
  `/home/anon/tools` holds ad-hoc tool installs).
- **Android SDK** at `/home/anon/android-sdk` (`platforms;android-36.1` — required
  by `minorApiLevel = 1` — `build-tools;36.0.0`, licenses accepted). Export
  `ANDROID_HOME`; there is no `local.properties`. Toolchain: compileSdk 36,
  minSdk 24, targetSdk 36, Java 11 bytecode, AGP 9.1.1, Kotlin 2.4.21.
- Output: `app/build/outputs/apk/debug/app-debug.apk` (~18 MB). Check with
  `/home/anon/android-sdk/build-tools/36.0.0/aapt dump badging <apk>`.
- Order: `:app:lintDebug` → `:app:testDebugUnitTest` → `:app:assembleDebug`.
- Single test class:
  `gradle :app:testDebugUnitTest --tests 'com.example.PalometaParserTest'`
- Release-signed APK: source the signing env first (keystore lives **outside**
  the repo: `/home/anon/keys/palometa-upload.jks`, alias `upload`, valid to
  2054; env file `/home/anon/keys/palometa-upload.env`, mode 600 — never commit
  credentials):

      . /home/anon/keys/palometa-upload.env
      JAVA_HOME=/home/anon/tools/jdk-21.0.13.1+2 ANDROID_HOME=/home/anon/android-sdk \
        /home/anon/tools/gradle-9.3.1/bin/gradle :app:assembleRelease

  Output: `app/build/outputs/apk/release/app-release.apk` (~12 MB), signed by
  the upload cert — verify with
  `/home/anon/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>`.
  The build's fallback path `my-upload-key.jks` at the repo root is **not**
  gitignored — prefer these env vars.

## Gotchas

- `app/build.gradle.kts` signingConfigs have "do not remove" comments — they are
  required by AI Studio. Debug signing points at `$rootDir/debug.keystore`
  (gitignored, absent from a fresh clone). It exists locally now; regenerate
  with `keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`
  if missing.
- `.env` (gitignored) / `.env.example` feed the secrets-gradle-plugin, but the
  project uses **no secrets** (`GEMINI_API_KEY` removed — no
  `BuildConfig` secret references anywhere). `google-services.json` is optional
  (`googleServices.missing.passthrough=true`, strategy WARN).
- All dependencies must be declared in `gradle/libs.versions.toml`;
  `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS`. Unused deps are left
  **commented out** in `app/build.gradle.kts` by design — uncomment instead of
  re-declaring them.
- `gradle.properties` quirks are deliberate: configuration cache,
  `workers.max=4`, `kotlin.compiler.execution.strategy=in-process` (avoids
  "Could not connect to Kotlin compile daemon"). Don't "fix" them.

## Testing

- JVM suites are self-contained: `PalometaParserTest` builds synthetic DEX/ELF
  bytes in code — no fixture files, no device, no services.
- `ExampleRobolectricTest` (`@Config(sdk = [36])`) downloads Robolectric
  `android-all` jars from Maven on first run (network required).
- Only the template `androidTest` exists; `connectedDebugAndroidTest` needs a
  device/emulator.
- Android lint (AGP default) is the only static check; there is no CI to match.

## Open TODOs (Play release)

- [x] **Decide the final `applicationId` before first Play upload** — finalized as
  `com.palometa.application` (becomes **permanent** on Play at first publish;
  the `com.example` namespace stays as-is).
- [ ] **Build the Play-required App Bundle** — `:app:bundleRelease` (using the
  signing env above) → `app/build/outputs/bundle/release/app-release.aab`.
  Upload to Play Console on an internal/closed track first; store listing
  still needs privacy policy URL, Data safety form, content rating, and
  assets (512px icon, 1024×500 feature graphic, 2+ phone screenshots).
