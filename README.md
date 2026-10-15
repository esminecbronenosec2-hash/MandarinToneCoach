# Mandarin Tone Coach 2.0 beta

An actual Android Mandarin practice app, designed to run **on a Samsung Galaxy Z Fold5 without a computer**. This is the replacement for the limited 20 KB prototype. Interface in Russian.

## What it does

- **Type arbitrary Mandarin text** (Chinese characters) into the training studio; also accepts **numbered pinyin** such as `ni3 hao3`.
- Pinyin4j looks up reading and tone candidates; a manual **1–5 tone editor** lets you correct polyphonic characters or sandhi.
- Listen using the device's Chinese **Text-to-Speech** engine (for completely offline TTS, install the system's offline Chinese voice).
- Record with microphone (up to 14 seconds); **local pitch tracker** extracts F0 contours and compares against expected pitch shapes, and labels approximate results by syllable.
- **Bundled Mandarin speech recognizer (Vosk)** reads what was said and shows a separate text-match statistic.
- 25 starter words/phrases, listening quiz, saved custom exercises, local progress and breakdown by weak tones.
- Dark responsive phone UI. No account, analytics, external API, or audio uploads.

### Why the APK will be much bigger

The GitHub Action downloads the Apache-2.0 **vosk-model-small-cn-0.22**, roughly **42 MB zipped**, during the build and embeds it in the APK. Model extraction on the first launch can take a while and additional local disk space. The model typically needs substantially more RAM when active. All recognition happens on-device.

This is not a system-wide speech-to-text model and does not implement multilingual ASR. Nor is it a proven speech-exam scoring system. **A high pitch-shape score does not guarantee correct vowels, consonants or word choice.** For multi-syllable phrases, syllable boundaries are estimated from duration, not forced alignment; scores and detected tones must be treated as approximate.

## Build APK from a Fold5 via Codespaces

1. Open your `MandarinToneCoach` repo in GitHub Codespaces in Chrome. You already have it set up from v1.
2. Download `MandarinToneCoach-v2-upload.zip` from this chat and upload it to the root of the Codespaces Explorer (the same way you uploaded v1).
3. Paste the following in the Codespaces terminal, adjusting the ZIP filename if Chrome added `(1)` or `-1`:

```bash
unzip -o MandarinToneCoach-v2-upload.zip
git add .github/workflows/build-apk.yml app build.gradle gradle.properties settings.gradle README.md .gitignore docs LICENSE
git commit -m "Mandarin Tone Coach v2: offline Chinese ASR and free practice"
git push origin main
```

4. In the repo **Actions** tab, open the newest **Mandarin Tone Coach v2 APK** run. It downloads the Chinese model and compiles/debug-tests Android. Once green, open **Artifacts → MandarinToneCoach-v2-offline-APK**, download the ZIP, extract `app-debug.apk`, and install it on Fold5.

See [instructions in Russian](docs/INSTALL_FOLD5_RU.md).

The first build can take 5–15 minutes depending on Android SDK and model download. If it fails, share the relevant failure log in chat; do not blindly hit rerun. If a device already has the old app, the Android application ID is the same (`com.mandarin.tonecoach`), but replacing the old debug build may require uninstalling the old version if the debug signatures differ. An uninstall deletes the old version's saved statistics. Neither app accesses the microphone until Android grants permission.

## Implementation

- Java 17 / Android SDK 35 / Gradle 8.9 / Android Gradle Plugin 8.7.3.
- UI: self-contained HTML/CSS/JavaScript in **Android WebView** (`app/src/main/assets/index.html`), connected to Android Java through a local JavaScript bridge. No network dependencies for the UI.
- Speech: `com.alphacephei:vosk-android:0.3.75@aar` + JNA `5.18.1@aar`, model downloaded from the model publisher during CI and packaged into APK.
- Pinyin: `com.belerweb:pinyin4j:2.5.1` (dictionary-based; multiple readings not automatically contextualized).
- F0: local normalized-difference (YIN-inspired) approach, 16-kHz PCM. Scores are **similarity of shape**, not phonemic recognition.
- Storage: `SharedPreferences` on-device for saved phrases and summary of results; **raw recordings are not persisted**.
- Sources are human-editable; no hidden commercial APIs or keys.

## Limitations / roadmap

1. **Syllable alignment** of connected speech is currently time-based. A robust production scorer should use forced alignment, prosody context and annotated Mandarin utterances. It is not implemented here.
2. Third-tone sandhi uses a simple rule and is not perfect for sentence-level syntax; always review expected tones.
3. TTS is delegated to the Android OS; availability of an offline Chinese voice depends on device settings.
4. Model is unpacked to app-specific storage once; delete app data to trigger re-extraction.
5. This is a first **functional beta**, not yet device-tested on your Fold5. Gradle and Android SDK are not installed in the assistant's local file-generation environment.

## Credits and licenses

See [`docs/THIRD_PARTY.md`](docs/THIRD_PARTY.md). Project code is licensed under MIT. Vosk and its model are Apache 2.0; Pinyin4j is BSD. See each project's upstream license for exact terms.
