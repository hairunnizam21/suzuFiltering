# suzuFiltering

Android app that takes any IPTV playlist (M3U / M3U8 standard, or the user's
custom `=== Section ===` format), probes every channel to classify each as
**Working** or **Error**, lets you search/filter the result, and downloads just
the slice you want as a clean text playlist.

| Build | applicationId | versionName |
|---|---|---|
| [`apk/suzuFiltering-latest.apk`](apk/suzuFiltering-latest.apk) | `com.suzufiltering.app` | 1.1.0 |

## What this build does for you

1. **Universal parser** — both `#EXTM3U` / `#EXTINF` and the custom

   ```
   === TV Malaysia ===

   TV1
   https://example.com/tv1.png
   https://example.com/tv1.mpd
   912760c4...:bea2d0f8...
   ```

   format are read by the same parser. Channel groups are preserved on download.
2. **Big-file safe** — playlists up to 50 MB are imported on a background thread
   with streaming I/O. The previous force-close on a 10 MB `.txt` file is gone:
   any error is now caught and surfaced as a toast instead of crashing.
3. **Working / Error filter** — each channel is probed concurrently (HEAD then
   GET fallback) and classified by HTTP status + content-type sniffing.
4. **Search** — both tabs have a search box that matches case-insensitively
   against channel name, group, and URL.
5. **Download Working Channel / Download Error Channel** — write a clean
   `Working.txt` / `Error.txt` (in the user-preferred custom format with
   section headers + Name / Logo / URL / KID:Key) to
   `Downloads/suzuFiltering/`.
6. **Share-URL normalization** — Google Drive, Dropbox, GitHub blob, and
   OneDrive share URLs are auto-converted to direct-download URLs at import
   time.
7. **ClearKey repair** — UUID-form KIDs with stripped leading zeros (e.g.
   `912760c4-9eb-5aff-3e06-0422c502f410`) are repaired before being passed to
   the player, preventing the `KID must be 16 bytes, got 15` crash.

## Install

1. Download [`apk/suzuFiltering-latest.apk`](apk/suzuFiltering-latest.apk).
2. On your phone or TV box, enable **Install unknown apps** for your file
   manager / browser.
3. Tap the APK to install. If a previous suzuFiltering build is installed,
   uninstall it first — the signing key changes between debug builds.

## How to use

1. Tap **Import & Filter** on the home screen.
2. Choose **URL**, **Paste**, or **File** as your input.
3. Tap **Start filter** — the app probes every channel concurrently with a
   short HEAD/GET and shows live progress.
4. Switch between the **Working** and **Error** tabs.
5. Type in the search box to filter the visible tab.
6. Long-press a channel to **Play** (sanity-check it in ExoPlayer) or
   **Move to Error / Working** (manual override).
7. Tap **Download Working Channel** or **Download Error Channel** — the file is
   written to `Downloads/suzuFiltering/`.

## Build from source

```bash
./gradlew :app:assembleDebug
```

The signed debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. The
unit tests for parser, KID normalization, and URL conversion live in
`app/src/test/`:

```bash
./gradlew :app:testDebugUnitTest
```

## Notable files

| File | Purpose |
|---|---|
| `M3uParser.kt` | Universal parser (EXTINF + custom `=== Section ===`) |
| `HealthChecker.kt` | Concurrent HTTP probe with HEAD/GET fallback |
| `M3uExporter.kt` | Writes channels back in the custom section format |
| `KidUtils.kt` | Normalizes malformed UUID KIDs |
| `UrlUtils.kt` | Share-URL conversion + MIME-from-URL detection |
| `ImportActivity.kt` | URL / Paste / File import with 50 MB streaming cap |
| `FilterResultActivity.kt` | Working/Error tabs + search + download buttons |
| `PlayerActivity.kt` | ExoPlayer with ClearKey DRM data: URL |

## CI

The GitHub Action under [`.github/workflows/build.yml`](.github/workflows/build.yml)
runs unit tests and builds a debug APK on every push, uploading the APK as an
artifact.
