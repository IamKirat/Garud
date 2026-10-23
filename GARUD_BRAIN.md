# GARUD — Project Brain

Continue this repository in place. Do not rebuild the app from scratch. Treat source code and test results as authoritative; older project notes may be stale.

## Product and privacy rules

- GARUD is an Android safety app. The notification listener can inspect only text Android exposes in notifications; it cannot read private conversations or arbitrary SMS history.
- Local rule screening runs first. Full message text is not persisted in finding history.
- Cloud analysis is optional and off by default. If enabled in Settings and an HTTPS backend URL is configured, exposed notification or manually pasted text is sent to that server, which may forward it to configured Gemini and embedding providers.
- VirusTotal submission requires a separate explicit `consent: true` request because submitted URLs enter VirusTotal's shared dataset.
- Never put provider keys in Android, print keys, or claim live provider/device verification without evidence. Previously shared keys must be rotated before live calls.

## Current verified implementation (2026-10-23)

### Android

- Kotlin/Compose app, package `com.garud.security`, min SDK 26, target/compile SDK 35.
- `NotificationAnalysisEngine` screens exposed notification content and manual messages using the same local rules.
- `GarudNotificationListenerService` handles listener state, content extraction (including MessagingStyle bundles), empty content, exclusions, pause, retry-safe dedup, background work, and alerts.
- `MessageAnalysisService` is the shared manual/notification entry point. It applies local rules first; optional HTTPS backend calls are consent-gated, time-bounded, response-parsed, and fail back to the local result. Higher local risk is retained.
- Settings exposes notification access, monitoring, history, cloud consent, and HTTPS backend URL. History-off alerts open Home rather than a detail page for a finding that was not saved.
- Home displays permission/listener and pipeline state. Theme palette follows GARUD colors.
- Source-verified offline RAG data is currently unavailable. The app no longer cites the old hand-written summaries as verified evidence; it labels source review as pending.

### Backend

- Express + TypeScript entry point is `server/src/index.ts`.
- `/api/analyze` defaults `cloudConsent` to false. Gemini generation and Gemini query embeddings/Qdrant retrieval run only after explicit cloud consent. The route validates citations against actual retrieved passage IDs and URLs.
- Gemini config defaults to `gemini-3.8-flash` generation and `gemini-embedding-001` embeddings. Gemini's embedding operation is separate and uses retrieval-query/document task types. The embedding model's default 3072 dimension matches the Qdrant collection size.
- `/health/providers` performs read-only Qdrant collection inspection and reports configured provider state without returning credentials.
- `/api/analyze/url` uses VirusTotal v3 and requires explicit consent. Pending, missing-key, invalid, and provider-error states are not described as clean.
- `server/src/rag/chunker.ts` creates sentence-boundary chunks, stable chunk IDs, SHA-256 hashes, and deterministic Qdrant UUIDs. Metadata requires HTTPS source URLs, language pattern, license, source timestamp, review status, and a review timestamp for VERIFIED records. Qdrant retrieval/upsert rejects unreviewed records and incorrect vector dimensions.
- Existing hard-coded source summaries lack exact provenance and license review. They are marked PENDING, not cited, and not ingested. Therefore the RAG plumbing exists but no verified source corpus is currently available in this checkout.
- Ten user-provided notification records are available as `server/src/data/garud_notification_chunks.jsonl`. They are synthetic response guidance, with null external-source provenance; they are kept distinct from VERIFIED evidence, can be retrieved semantically, and are never accepted as citations or proof.
- `server/src/scripts/importNotificationGuidance.ts` validates all ten exact IDs and SHA-256 text hashes, checks the existing 3072-dimensional collection read-only, embeds with the configured Gemini embedding model, upserts deterministic IDs in small batches, reads payloads back, and runs five semantic checks. It requires `GARUD_KEYS_ROTATED=true` in the process environment before any provider call.
- `server/src/ai/providerAdapter.ts` is legacy Kimi-compatible code and is not used by the current `/api/analyze` route.

## Latest verification

- Android: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug` succeeded. Gradle XML reports 30 unit tests. Instrumentation tests were compiled but not run; no app was installed.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 20,285,567 bytes, built 2026-10-23 04:45:26 local time.
- Backend after notification-guidance importer changes: `npm run build` succeeded; `npx tsx --test src/test/pipeline.test.ts` passed 13/13 tests. Qdrant/Gemini live import and remote semantic checks were not run because the key-rotation gate is unset (`GARUD_KEYS_ROTATED` is false in the current shell); uploaded count remains 0.
- Provider calls were not made. Credentials must be rotated before live testing. Qdrant/Gemini/VirusTotal connectivity is not verified.
- Physical notification and Skip-button taps were not verified. ADB could not connect to a device. `WelcomeScreenTest` exists and its instrumentation source compiles.
- Git has no committed baseline: project files currently show as untracked. Preserve existing workspace contents; a normal tracked-file `git diff` cannot represent the baseline.

## Changed in the latest implementation pass

- Android: `AndroidManifest.xml`, `GarudApplication.kt`, `MainActivity.kt`, `GarudPreferences.kt`, `GarudNotificationListenerService.kt`, new `MessageAnalysisService.kt`, `SettingsScreen.kt`, `ManualMessageAnalysisScreen.kt`, `FindingDetailScreen.kt`, `Theme.kt`, and RAG/notification unit tests.
- Backend: `index.ts`, `config/env.ts`, `types/index.ts`, `services/geminiService.ts`, `services/qdrantService.ts`, `rag/vectorRetriever.ts`, `rag/evidenceRetriever.ts`, `rag/citationValidator.ts`, new `rag/chunker.ts` and `rag/metadata.schema.json`, `scripts/seedKnowledge.ts`, and `test/pipeline.test.ts`.
- Updated `server/.env.example` with blank secrets and verified model defaults. Do not copy old shared credentials; rotate them first.

## Remaining work and exact next action

1. Rotate previously shared provider credentials, then run the synthetic-guidance importer by setting `GARUD_KEYS_ROTATED=true` in the process environment and executing `npm run import:notification-guidance` from `server/`.
2. Source-backed evidence still requires permitted source material with exact source URLs, license, publication timestamps, language, and completed provenance review. Configure rotated provider keys only in `server/.env`; run `GET /health/providers` after rotation.
3. Deploy the backend to an HTTPS host and enter that base URL in Android Settings. Verify opted-in manual and notification analysis on a test device, then verify permission revocation and network/malformed-response fallback. Do not install an APK without user authorization.

## Focused Android UI/features pass (2026-10-23)

- Added a four-item animated bottom navigation for Home, Third Eye, History, and Settings, plus Home quick actions for message analysis, link checks, QR scans, and microphone transcription.
- Added HTTPS URL normalization and consent-gated `/api/analyze/url` requests through `UrlReputationClient`. The screen does not open links automatically and reports pending/provider errors without calling them safe.
- Added Google Play services QR scanning on tap and image-based decoding. The app receives decoded text and does not open it automatically.
- Added on-device UI flow for Android speech recognition with an explicit microphone consent and runtime permission. This uses nearby microphone audio, cannot capture phone-call system audio, and GARUD does not save audio or transcript.
- Added persisted System/Light/Dark appearance, storage figures, cache-only clearing with confirmation, and local finding-history deletion. The home clear-data action explains that it deletes GARUD's stored finding data only.
- Added history loading/error/empty states and entry animations; replaced privacy copy that incorrectly claimed no network use with an accurate local-first and optional-cloud explanation.
- A secure arbitrary custom-LLM provider setting is not implemented: the current Android/backend contract has no authenticated provider manager, and placing a provider key in the client would expose it.
- Latest verification: `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug` succeeded. Unit tests: 33 passed, 0 failed. Android instrumentation sources compiled; tests were not run on a device. ADB/device validation remains unavailable.
- Launcher icon uses the eagle mark from the user-provided image, with adaptive icon foreground/background resources for Android 8+ and density-specific PNGs for earlier launchers. Source artwork is in `app/src/main/res/drawable-nodpi/garud_icon_full.png` and `garud_icon_foreground.png`.
- After adding the launcher icon, `:app:assembleDebug` succeeded. Latest debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 24,047,644 bytes, built 2026-10-23 local time. It was not installed on a device.
- VirusTotal API key was configured in ignored `server/.env` for the backend URL-analysis route. Keep it server-side; it is not in Android resources or the APK. No live VirusTotal request was made, so provider validity/connectivity is unverified.
- Removed online/Supabase onboarding. Welcome now uses a local demo password field prefilled with `123456`, keeps Forgot Password as an informational demo dialog, and lets users Skip without a password. This is not account security.
- Restored the Skip button's top z-order so it receives taps above the scrollable welcome content. The existing welcome instrumentation source covers Skip and the password screen; it has not been run on a device.
- Clarified URL scanner backend setting: it requires the public HTTPS base URL of a deployed GARUD server implementing `/api/analyze/url`. The VirusTotal API key is not a backend URL, and the Express backend is not deployed/configured to a public host in this checkout.
- Latest Android build after removing Supabase and fixing Skip layering: `:app:assembleDebug` succeeded. URL scanning was not exercised against a deployed HTTPS backend.

APK build and local Android tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Backend verification:

```powershell
npm run build
npx tsx --test src/test/pipeline.test.ts
```
