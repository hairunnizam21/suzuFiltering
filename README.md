# suzuFiltering

IPTV playlist filtering app for Android. Imports an M3U / M3U8 playlist (URL, paste,
or file), probes every channel with a tiny HTTP HEAD/GET, classifies each one as
**Working** or **Error**, and lets you download just the working subset (or just the
error subset) back out as a clean playlist.

The APK in [`apk/`](apk/) is the latest signed build, ready to side-load on any
Android phone or TV box (min SDK 21, arm64-v8a).

| Build | applicationId | versionName |
|---|---|---|
| [`apk/suzuFiltering-latest.apk`](apk/suzuFiltering-latest.apk) | `com.suzufiltering.app` | 1.0.0 |

## What's in this build

- **Rename:** the two action buttons on the Filter Result screen are now labeled
  **"Download Working Channel"** and **"Download Error Channel"** (previously
  "Muat turun Working" / "Muat turun Error").
- **Patch tool:** the APK was produced by re-packaging the upstream APK with
  [apktool] after editing `res/values/strings.xml`, then signed with a fresh debug
  keystore via Android `apksigner`. The procedure is reproducible from the
  workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Install

1. On your Android device, enable **Install from Unknown Sources** for your file
   manager / browser.
2. Download `apk/suzuFiltering-latest.apk`.
3. Tap to install. If you have a previous suzuFiltering build, uninstall it first
   (the signing key changes between Devin-built debug APKs and any upstream signed
   release).

## What suzuFiltering does

- **Import:** paste a URL, paste the playlist text, or pick a `.m3u` / `.m3u8`
  file from local storage.
- **Filter:** every channel is probed concurrently with a short HEAD/GET. The
  app classifies each as Working (200 + correct content-type / non-HTML body)
  or Error (HTTP error, timeout, HTML error page, etc.).
- **Edit:** tap any channel to rename, change the logo, or move it between the
  Working and Error tabs.
- **Download:** the **Download Working Channel** / **Download Error Channel**
  buttons write a clean `Working.txt` / `Error.txt` (M3U-formatted) to your
  Downloads folder.

## Known limitations of this build

- The bundled `M3uParser` recognizes only standard `#EXTM3U` / `#EXTINF` syntax
  plus `#KODIPROP` / `#EXTVLCOPT` license hints. The custom "`=== Section ===`"
  format with bare lines (name / logo / URL / KID:KEY) is **not** yet parsed —
  if you import that format, channels will be detected as raw URLs without
  group titles. To enable the custom format we need to ship a parser update
  (TODO).
- Smali sources for this repo are intentionally not committed: the binary
  bytecode is large (~150 MB across all `smali*/` directories) and unmaintainable
  by hand. If you want to iterate on the app's Kotlin source, the upstream
  AnimedanTV/SuzuTV Kotlin source lives at
  https://github.com/hairunnizam21/simpletv — clone that repo, port the
  filtering features, then rebuild.

## Reproducing the patch

```bash
# 1. Decode the upstream APK
java -jar apktool.jar d suzuFiltering+tolongfix.apk -o decoded

# 2. Edit res/values/strings.xml:
#    "Muat turun Working" -> "Download Working Channel"
#    "Muat turun Error"   -> "Download Error Channel"

# 3. Rebuild
java -jar apktool.jar b decoded -o suzuFiltering-unsigned.apk

# 4. Align + sign with a debug keystore
zipalign -p -f 4 suzuFiltering-unsigned.apk suzuFiltering-aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out suzuFiltering-latest.apk suzuFiltering-aligned.apk
apksigner verify suzuFiltering-latest.apk
```

The GitHub Action under [`.github/workflows/build.yml`](.github/workflows/build.yml)
does this end-to-end on every push and uploads the result as a workflow artifact.

[apktool]: https://apktool.org
