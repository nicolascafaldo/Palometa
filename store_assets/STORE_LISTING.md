# Google Play Store Listing — Palometa

Ready-to-paste copy and asset checklist for **Google Play Console** (`com.palometa.application`).

---

## 1. Graphics Checklist (`store_assets/`)

| Asset | File | Spec | Status |
| --- | --- | --- | --- |
| High-Res App Icon | `store_assets/play_icon_512.png` | `512 × 512 px` PNG | Ready |
| Feature Graphic | `store_assets/feature_graphic_1024x500.png` | `1024 × 500 px` PNG | Ready |
| Phone Screenshots | *(capture on emulator/device)* | 2–8 screenshots (`9:16` or `16:9`, ≥ 320px) | Capture needed |

### Recommended Phone Screenshots to Capture
1. **Home Intake Screen** — showing the piranha illustration and one-tap APK / Installed Apps actions.
2. **Overview Screen** — showing APK structure breakdown bar, SHA-256/MD5 hashes, and signing schemes.
3. **Smali & DEX Explorer** — showing syntax-highlighted Smali source in the scrollable code viewer.
4. **Printable Strings & Leak Scanner** — showing categorized URLs, tokens, or paths with `"Leaks Only"` filtering.
5. **Ghidra Export Studio** — showing ABI filters, `import_manifest.json` preview, and `analyzeHeadless` command.

---

## 2. Store Listing Copy

### App Name (8 / 30 chars)
```text
Palometa
```

### Short Description (78 / 80 chars)
```text
Inspect APKs, read Smali code, scan strings & export Ghidra bundles on mobile.
```

### Full Description (≈ 1,850 / 4,000 chars)
```text
Palometa is a mobile APK inspector, Smali disassembler, and Ghidra desktop preparation workbench built for Android developers, security auditors, and reverse engineers.

Inspect installed applications or .apk packages directly on your phone — completely offline, with zero broad storage or tracking permissions — then package clean, structured workspaces ready for desktop disassembly in Ghidra.

KEY FEATURES

• Browse Installed Apps & Storage APKs
Inspect apps installed on your device, pick .apk files directly from your Downloads folder, mount storage directories via Android's Storage Access Framework, or test immediately with the built-in self-APK demo.

• DEX Explorer & Syntax-Highlighted Smali
Browse multi-DEX archives (classes.dex, classes2.dex, ...), filter classes and packages, inspect fields and method prototypes, and read decompiled Smali source with syntax highlighting and Dalvik bytecode offsets.

• Printable String & Leak Scanner
Scan DEX string pools, native .so binaries, the decoded manifest, and text/JSON assets in one place. Filter results by URLs, IPv4 addresses, API key/token patterns, emails, hardcoded secret hints, and file paths.

• Native ELF (.so) & Manifest Inspection
Examine ELF32/ELF64 headers, section flags, .dynsym dynamic symbols, JNI_OnLoad and Java_* exports, and decoded AndroidManifest.xml XML alongside a built-in binary hex viewer.

• Ghidra Workstation Bundle Export (.zip)
Package DEX files, ABI-filtered native .so libraries, decoded AndroidManifest.xml, signing metadata, import_manifest.json, a post-import Python helper script (ghidra_import_helper.py), and ready-to-run analyzeHeadless commands into a shareable ZIP archive.

PRIVACY & PERMISSIONS
• Runs 100% locally on your device — no servers, accounts, analytics, or ads.
• Uses Android's standard document picker and targeted launcher-app visibility with zero broad storage permissions.

DISCLAIMER & AUTHORIZED USE
Palometa is intended strictly for software developers, security researchers, and educators auditing applications they own or are explicitly authorized to inspect. Users are solely responsible for complying with applicable laws, terms of service, and intellectual property rights. Provided "AS IS" under the MIT License; the developer assumes no responsibility or liability for how users operate this software or use exported files.
```

---

## 3. Categorization & Contact Fields

- **App or Game:** App
- **Category:** Tools *(or Productivity)*
- **Tags:** Developer tools, Utility
- **Contains Ads:** No
- **License:** MIT License (`LICENSE` in project root — Copyright (c) 2026 Nicolas H. Cafaldo)
