# SnapRelay — Engineering Design Document

**Project name** : **SnapRelay** 

**Type:** Internal-use native Android application (not distributed via Play Store)
**Purpose:** Tripod-mounted phone photographs a laptop screen on Volume-Up press; image is saved and pushed to Telegram in the background while the live preview keeps running.

---

## 1. Design Philosophy

Three constraints drive every decision in this document:

- **The camera is a fixed rig, not a general-purpose photography tool.** The phone/laptop geometry never changes during a session (angle, distance, lighting are roughly static once set up). This means "lock and forget" controls (focus lock, exposure lock, persisted manual settings) matter far more than auto-everything convenience features. although there should be toggle between locking indiviudal controls or making them automatic
- **Capture must never be blocked by I/O.** Every capture-to-upload path is fire-and-forget from the UI/camera thread's perspective. The moment a JPEG is safely on disk, the capture pipeline is done; everything past that point (encoding cleanup, queueing, network) happens on background coroutines with their own lifecycle, independent of whether you press Volume Up again a second later.
- **Simplicity over completeness.** No DI framework, no Room, no backend. A single-activity Compose app, a handful of singleton-style manager classes, DataStore for persistence, and a plain file-backed queue. This is intentional: fewer moving parts means fewer places for a background upload to silently die.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI Layer (Compose)                     │
│   CameraScreen        SettingsScreen        LogScreen            │
│        │                    │                    │               │
│        ▼                    ▼                    ▼               │
│                    ┌──────────────────┐                          │
│                    │   AppViewModel    │  (single shared VM,     │
│                    │  (StateFlow hub)  │   or one per screen     │
│                    └────────┬─────────┘   backed by shared repos)│
└─────────────────────────────┼───────────────────────────────────┘
                               │
        ┌──────────────────────┼───────────────────────┐
        ▼                      ▼                       ▼
┌───────────────┐     ┌────────────────┐     ┌──────────────────┐
│ CameraManager  │     │ SettingsRepo    │     │  LogRepository    │
│ (CameraX +     │     │ (DataStore)     │     │ (in-memory ring   │
│  Camera2       │     │                 │     │  buffer + flow)   │
│  Interop)      │     └────────────────┘     └──────────────────┘
└──────┬────────┘
       │ captured JPEG (File)
       ▼
┌────────────────────┐        ┌─────────────────────────┐
│  CaptureRepository   │─────▶│  UploadQueueManager       │
│  (writes file,       │      │  (persistent queue,       │
│   emits CaptureEvent)│      │   coroutine worker loop,  │
└────────────────────┘        │   retry/backoff)          │
                               └───────────┬─────────────┘
                                           ▼
                               ┌─────────────────────────┐
                               │  TelegramUploader         │
                               │  (OkHttp multipart,       │
                               │   sendDocument)           │
                               └─────────────────────────┘
```

**Data flow in one sentence:** Volume-Up → CameraManager.captureNow() → JPEG hits disk → CaptureRepository fires a `CaptureEvent` → UploadQueueManager enqueues + persists the queue entry → a long-running worker coroutine drains the queue serially against TelegramUploader → success/failure is logged and reflected in UI state via StateFlow.

---

## 3. Module / Folder Structure

```
app/
 └── src/main/java/com/snaprelay/
      │
      ├── SnapRelayApp.kt                 // Application class, initializes singletons
      ├── MainActivity.kt                 // Single Activity, hosts NavHost
      │
      ├── ui/
      │    ├── camera/
      │    │    ├── CameraScreen.kt
      │    │    ├── CameraViewModel.kt
      │    │    ├── CameraPreviewView.kt      // AndroidView wrapper for PreviewView
      │    │    └── components/              // ISO/exposure/focus HUD chips, shutter button
      │    ├── settings/
      │    │    ├── SettingsScreen.kt
      │    │    ├── SettingsViewModel.kt
      │    │    └── components/               // grouped setting sections
      │    ├── logs/
      │    │    ├── LogScreen.kt
      │    │    └── LogViewModel.kt
      │    └── theme/                         // minimal Compose theme, no animation lib
      │
      ├── camera/
      │    ├── CameraManager.kt               // CameraX lifecycle, use-case binding
      │    ├── Camera2ControlBridge.kt         // Camera2Interop CaptureRequest.Key writes
      │    ├── CameraCapabilities.kt           // introspects CameraCharacteristics, reports what's supported
      │    ├── CameraSettingsState.kt          // in-memory mirror of current camera settings
      │    └── VolumeKeyCaptureHandler.kt      // hooks Activity.onKeyDown for VOLUME_UP
      │
      ├── capture/
      │    ├── CaptureRepository.kt           // owns file naming, storage location, save-to-disk
      │    └── CaptureEvent.kt                // sealed class: Captured, EncodeFailed, etc.
      │
      ├── upload/
      │    ├── UploadQueueManager.kt          // enqueue, persist, drain loop, retry/backoff
      │    ├── UploadTask.kt                  // data class: id, filePath, attempts, status, timestamps
      │    ├── UploadQueueStore.kt            // DataStore/JSON-backed persistence for the queue itself
      │    └── TelegramUploader.kt            // OkHttp client, sendDocument multipart call
      │
      ├── settings/
      │    ├── SettingsRepository.kt          // DataStore Preferences wrapper
      │    └── AppSettings.kt                 // data class: token, chatId, camera defaults, upload prefs
      │
      ├── logging/
      │    ├── LogRepository.kt               // ring buffer + SharedFlow<LogEntry>
      │    └── LogEntry.kt
      │
      └── common/
           ├── Result.kt / SnapRelayError.kt  // typed error hierarchy
           └── Dispatchers.kt                 // injectable dispatcher provider for testability
```

**Why this shape:** each top-level package is a *pipeline stage* (camera → capture → upload) or a *cross-cutting concern* (settings, logging), not a generic "data/domain/presentation" split. For a project this size, organizing by feature/pipeline-stage keeps related files physically close and avoids the interface-per-class ceremony that a strict clean-architecture layering would add without payoff here.

---

## 4. Module Responsibilities

### 4.1 `camera/CameraManager`
- Owns the `ProcessCameraProvider`, `Preview`, and `ImageCapture` use cases.
- Binds/unbinds use cases to the activity lifecycle exactly once; survives configuration such as returning from Settings.
- Exposes a single suspend function `captureNow(): CaptureResult` that the volume-key handler and the on-screen shutter button both call — **one capture code path, two triggers**, so behavior can never drift between the hardware button and the UI button.
- Delegates manual-control writes (ISO, shutter, focus distance, AWB) to `Camera2ControlBridge` via `Camera2Interop.Extender`.
- Applies persisted `CameraSettingsState` on every camera bind (so re-opening the app after a phone reboot restores manual focus distance, exposure, etc., without user intervention).

### 4.2 `camera/Camera2ControlBridge`
- Thin wrapper translating high-level intents ("lock focus at current distance", "set ISO 200") into `CaptureRequest.Key` values applied through `Camera2Interop.Extender` on the `Preview`/`ImageCapture` builders.
- Central place that queries `CameraCharacteristics` to check whether a given key (e.g., `CONTROL_AE_LOCK`, `LENS_FOCUS_DISTANCE`, `SENSOR_SENSITIVITY`) is actually writable on this device, and reports "unsupported" back up rather than silently failing.

### 4.3 `camera/CameraCapabilities`
- Runs once at camera-open time. Produces a `CameraCapabilityReport` (which manual controls exist, min/max ISO, min/max exposure, supported AF/AE modes, whether Camera2 Level is LEGACY/LIMITED/FULL).
- The Settings screen reads this report to decide which sliders to show vs. gray out — no dead controls in the UI.

### 4.4 `camera/VolumeKeyCaptureHandler`
- A tiny class the `MainActivity` delegates `onKeyDown`/`onKeyUp` to for `KEYCODE_VOLUME_UP`.
- Debounces double-fires (some OEMs deliver both up/down events; some deliver repeated key-down while held) and calls `CameraManager.captureNow()` exactly once per physical press.
- Must consume the event (return `true`) so the system does **not** also change media volume while shooting.

### 4.5 `capture/CaptureRepository`
- Given the raw JPEG bytes/`ImageProxy` from `ImageCapture.OnImageSavedCallback`, decides the filename (timestamp-based, e.g. `2026-07-31_142033_001.jpg`) and target directory (app-specific external storage — no `MediaStore` needed since these aren't meant for the user's gallery).
- Writes to disk, then emits a `CaptureEvent.Captured(file)` on a `SharedFlow` that `UploadQueueManager` collects.
- This is the **only** place capture and upload are coupled — and it's a one-directional event, not a function call, which is what lets capture return instantly regardless of upload queue depth.

### 4.6 `upload/UploadQueueManager`
- Maintains an in-memory `MutableStateFlow<List<UploadTask>>` mirrored to disk via `UploadQueueStore` (a small JSON file in app-private storage — this is the "absolutely necessary" case where lightweight persistence earns its keep instead of Room; a flat JSON list is enough for a queue that's realistically tens of items deep).
- A single long-lived worker coroutine (`Dispatchers.IO`, launched from `SnapRelayApp`'s `ProcessLifecycleOwner` scope, not the Activity) loops: pop oldest `PENDING` task → call `TelegramUploader.upload()` → mark `SUCCESS`/`RETRY_SCHEDULED`/`FAILED` → persist → repeat.
- On `SUCCESS`, optionally deletes the local file per the "delete after upload" setting.
- On failure, applies exponential backoff (e.g., 2s, 4s, 8s… capped at 60s) up to a configurable max-attempts, after which the task is marked `FAILED` but **kept** (never silently dropped) and surfaced in the Log screen with a manual "retry" affordance.
- Because this queue is process-lifecycle-scoped rather than Activity-scoped, backgrounding the app briefly (e.g., switching to check something) doesn't kill in-flight uploads. (See §9 on the limits of this if the process itself is killed.)

### 4.7 `upload/TelegramUploader`
- Wraps a single shared `OkHttpClient` (with sane timeouts — see §8) and performs `POST https://api.telegram.org/bot<token>/sendDocument` as multipart form data with fields `chat_id` and `document` (the file).
- `sendDocument` is used specifically because Telegram's `sendPhoto` re-encodes/compresses images for the photo pipeline; `sendDocument` transmits the file as-is, which matters here because the whole point is preserving screen text legibility.
- Returns a sealed `UploadResult` (`Success`, `RetryableFailure(reason)`, `PermanentFailure(reason)`) so the queue manager can distinguish "network blip, retry" from "401 bad token, don't retry forever."

### 4.8 `settings/SettingsRepository`
- Wraps Jetpack DataStore Preferences. Stores: Telegram bot token, chat ID, all camera manual-control values + lock states, JPEG quality, resolution choice, delete-after-upload flag, max retry count.
- Exposes a `Flow<AppSettings>` that both `CameraManager` (to re-apply on bind) and the Settings UI (to render current values) collect.

### 4.9 `logging/LogRepository`
- A bounded in-memory ring buffer (e.g., last 500 entries) backed by a `MutableSharedFlow<LogEntry>` for live tailing on the Log screen, plus optional persistence to a rolling text file for post-mortem debugging if the app crashes.
- Every stage (`Captured`, `Encoding`, `Queued`, `Uploading`, `Success`, `Retry`, `Failure`) writes exactly one line here — this becomes your primary debugging tool since there's no crash-reporting backend.

---

## 5. Threading / Coroutine Model

| Work | Dispatcher / Scope | Notes |
|---|---|---|
| CameraX preview + capture callbacks | CameraX's own internal executor (main-safe) | Don't block this; hand off immediately |
| JPEG write-to-disk | `Dispatchers.IO`, scoped to a short-lived coroutine launched per capture | Completes in low tens of ms typically |
| Upload queue worker | `Dispatchers.IO`, scoped to `ProcessLifecycleOwner.lifecycleScope` (app-process-wide, not tied to the Activity) | One single worker loop draining the queue; avoids N parallel uploads racing Telegram's rate limits |
| DataStore reads/writes | `Dispatchers.IO` (DataStore handles this internally) | — |
| UI state emission | `Dispatchers.Main.immediate` via `StateFlow`/`collectAsStateWithLifecycle` | Compose recomposition only, never does I/O |

**Why one worker, not N parallel uploads:** Telegram's Bot API rate-limits per-bot/per-chat sends. Serial upload with backoff is simpler to reason about, guarantees images arrive in capture order, and avoids the failure mode where 20 rapid captures spawn 20 concurrent HTTP requests that all trip a 429.

**Why process-scoped, not Activity-scoped:** rotating the screen, or briefly navigating to Settings and back, must not cancel an in-flight upload. Tying the worker's `CoroutineScope` to `ProcessLifecycleOwner` (or a custom `Application`-level `SupervisorJob` scope) instead of the Activity avoids this entirely.

---

## 6. Camera Pipeline

1. **Bind-time:** `CameraManager` builds `Preview`, `ImageCapture` (mode: `CAPTURE_MODE_MINIMIZE_LATENCY`, since sharp-but-fast beats zero-shutter-lag tricks here), and applies any Camera2Interop options (manual AE/AF/AWB) sourced from persisted settings.
2. **Steady state:** Preview runs continuously; if focus/exposure lock is enabled, `CONTROL_AF_TRIGGER`/`CONTROL_AE_LOCK` are set once and left alone — no per-shot AF scan, which is the single biggest latency and "hunting" reduction for a static rig.
3. **Capture trigger (Volume Up or on-screen button):** `CameraManager.captureNow()` calls `ImageCapture.takePicture()` targeting an in-memory `ImageProxy`/output stream (not `MediaStore`, since these files aren't meant to appear in the phone's gallery).
4. **On image captured:** bytes are hand off to `CaptureRepository` on `Dispatchers.IO`; this returns essentially immediately from the camera's perspective, and CameraX is free to accept the next `takePicture()` call.
5. **Preview never pauses.** Because the flow is preview-runs + snapshot-on-demand rather than preview-freeze-then-capture, there's no black-frame or reconfiguration gap between shots.

### Practical support levels
Manual sensor controls (`SENSOR_SENSITIVITY`, `SENSOR_EXPOSURE_TIME`, `LENS_FOCUS_DISTANCE`) are only guaranteed on devices reporting Camera2 hardware level `FULL` or `LEVEL_3`. Devices at `LIMITED` often support a useful subset (commonly AE lock, AF lock, sometimes manual ISO) but not full manual shutter. `LEGACY` devices should be treated as auto-only — `CameraCapabilities` detects this at runtime and the UI simply hides unsupported sliders rather than showing controls that silently no-op. This graceful degradation matters more here than in a typical photo app because you don't control which phone ends up on the tripod long-term.

---

## 7. Upload Pipeline (Detail)

```
CaptureEvent.Captured(file)
        │
        ▼
UploadQueueManager.enqueue(file)
        │  → creates UploadTask(id, path, PENDING, attempts=0)
        │  → persists queue snapshot to disk (crash-safety)
        │  → log: "Queued"
        ▼
Worker loop (single coroutine, runs continuously while app process alive)
        │
        ├─ pop oldest PENDING/RETRY_SCHEDULED task
        ├─ log: "Uploading"
        ├─ TelegramUploader.upload(task) ──┐
        │                                   ├─ Success → mark SUCCESS, optionally delete file, log "Success"
        │                                   ├─ RetryableFailure → attempts++, backoff delay, log "Retry"
        │                                   └─ PermanentFailure → mark FAILED, log "Failure" (kept for manual retry)
        └─ persist updated task, loop
```

**Never losing images:** the queue is persisted to disk on every state transition (enqueue, success, failure), so if the app process is killed mid-upload, on next launch `UploadQueueManager` reloads any `PENDING`/`IN_PROGRESS` tasks and resumes — the underlying JPEG file on disk is the source of truth, the queue JSON just tracks what's been sent.

---

## 8. Telegram Integration Design

- **Auth:** bot token stored in DataStore (see §12 for the security caveat — there is no truly secure place to keep a secret in an app with no backend). Chat ID likewise stored, set once via Settings ("send yourself the chat ID via `/start` and paste it in" is the typical bootstrap flow for a personal bot).
- **Upload call:** `multipart/form-data` POST to `sendDocument`, fields `chat_id`, `document` (binary), optionally `caption` (e.g., timestamp or sequence number for easy cross-referencing later).
- **Timeouts:** connect ~10s, write ~30s (JPEGs from modern sensors can be several MB, and upload conditions may be a phone hotspot or weak Wi-Fi), read ~15s.
- **Retries:** transient failures (timeout, `5xx`, connection reset) → retryable with exponential backoff. `4xx` other than `429` (bad token, chat not found, blocked bot) → permanent failure, surfaced clearly in logs so you notice immediately rather than after silently failing all night.
- **Rate limits:** Telegram's general guidance is roughly one message/second sustained per chat for a bot; the serial worker with backoff naturally respects this without extra rate-limiting code, since a burst of 20 captures just queues and drains at whatever pace `sendDocument` calls actually complete.
- **Large images:** Bot API document uploads support files up to 50 MB, comfortably above a typical high-res JPEG even at low compression — chunking isn't needed.
- **Security considerations:** the bot token is a bearer credential — anyone with it can send messages/files as your bot. Store it in DataStore (not plaintext in a log!), never print it in the Log screen, and treat the phone itself as the security boundary (if the phone is lost, rotate the token via BotFather). Since this is a personal/internal app off the Play Store, there's no code obfuscation requirement, but avoid committing the token into any shared repo — keep it entered via the Settings UI, not hardcoded.

---

## 9. State Management

- **Single source of truth per concern:** `AppSettings` (from `SettingsRepository`), `List<UploadTask>` (from `UploadQueueManager`), `List<LogEntry>` (from `LogRepository`) are each exposed as a `StateFlow`/`SharedFlow` and collected by the relevant Compose screens with `collectAsStateWithLifecycle()`.
- No cross-screen mutable shared state beyond these flows — Settings screen writes to `SettingsRepository`, Camera screen reads from it; they never talk to each other directly.
- **Process death:** if Android kills the app process outright (low memory, long background), the upload queue survives via persisted JSON + the JPEGs remain on disk; on relaunch the worker resumes exactly where it left off. In-memory-only state (current ISO reading on the HUD, etc.) is cheap to recompute from `CameraCapabilities`/settings on relaunch, so it's not persisted separately.

---

## 10. Settings Management

Persisted via DataStore Preferences (a single `Preferences` file is enough at this scale — no need for multiple DataStore instances):

- `telegram_bot_token`, `telegram_chat_id`
- `jpeg_quality` (0–100), `resolution_preset`
- `focus_mode` (continuous / single / manual), `manual_focus_distance`
- `exposure_mode` (auto / manual), `manual_iso`, `manual_shutter_speed_ns`, `exposure_compensation`
- `white_balance_mode`
- `af_lock_enabled`, `ae_lock_enabled` (the toggles requested for turning lock behavior on/off)
- `flash_mode` (off/on/torch)
- `delete_after_upload` (bool)
- `max_upload_retries`

All fields have safe defaults so first-launch works without any configuration beyond entering the Telegram token/chat ID.

---

## 11. Error Handling

A small sealed hierarchy (`SnapRelayError`) covers: `CameraBindError`, `CaptureFailedError`, `DiskWriteError`, `UploadNetworkError`, `UploadAuthError`, `UnsupportedControlError`. Each is caught at its originating layer, converted to a `LogEntry`, and — where user-actionable (e.g., "bad Telegram token") — surfaced as a persistent banner/status chip on the Camera screen rather than a transient toast that could be missed while you're focused on the laptop screen, not the phone.

---

## 12. Performance Considerations

- **Capture latency:** dominated by AF/AE convergence if locks aren't engaged — hence locking focus/exposure once for the fixed rig is the single highest-leverage optimization. `CAPTURE_MODE_MINIMIZE_LATENCY` on `ImageCapture` shaves further overhead versus the default max-quality pipeline mode (the max-quality mode does extra merging/processing that isn't needed for a JPEG-direct-off-sensor workflow like this).
- **JPEG encoding:** letting the camera HAL produce JPEG directly (rather than capturing YUV and re-encoding in-app) avoids an extra full-resolution encode pass — use `ImageCapture`'s built-in JPEG output unless a specific manual encoder is later needed.
- **Memory:** each captured image should be streamed to disk rather than held as a full decoded `Bitmap` anywhere in the pipeline; the upload path reads the file as a raw byte stream for multipart upload rather than decoding it into memory.
- **Upload queue:** serial draining bounds concurrent memory/network use even under a 20-shot burst; the JSON queue-state file stays tiny (a few hundred bytes per entry).
- **Battery:** continuous preview is the steady-state battery draw here (typical of any camera app); keeping the screen brightness low on the phone itself (irrelevant to the app, but worth noting) and avoiding unnecessary wakelocks in the upload worker (rely on the process staying alive rather than acquiring a partial wakelock, unless you observe Doze mode killing uploads — see the roadmap item on this) keeps drain reasonable for a tripod-mounted, presumably-charging device.
- **Storage:** JPEGs accumulate in app-private storage; the "delete after successful upload" setting is the main lever, plus consider a simple size/count cap with oldest-first eviction as a later refinement if the device isn't kept charging/connected.
- **Potential bottlenecks:** slow/unstable network (mitigated by queue + backoff, not by blocking capture), a device whose camera HAL doesn't honor `MINIMIZE_LATENCY` well (test on your actual target phone), and Doze/App-Standby potentially throttling background network for the upload worker if the app is left backgrounded for a long time on an unplugged phone (a wakelock or foreground-service promotion is the standard fix if this is observed).

---

## 13. Security Considerations

- Bot token and chat ID live in DataStore, which is private to the app's sandbox — adequate for a personal, sideloaded, single-device app; not a substitute for a proper secrets vault if this were ever multi-user or distributed.
- No backend means no server-side attack surface, but also means no server-side revocation beyond rotating the token in BotFender/BotFather.
- Logs must never print the bot token in full (mask it, e.g., show only the last 4 characters, if it's ever shown for debugging).
- Since it's sideloaded (not Play Store), standard Android install-from-unknown-sources caveats apply — keep the signing key and APK to yourself.

---

## 14. Development Roadmap & Milestones

**Milestone 1 — Camera skeleton**
Single-Activity Compose shell, CameraX preview binding, on-screen shutter button, basic JPEG-to-disk save. No Telegram yet, no manual controls yet. *Goal: confirm the phone can preview + snapshot reliably at the mounted angle.*

**Milestone 2 — Volume-key capture + manual controls**
Hook `KEYCODE_VOLUME_UP`, wire it to the same capture path as the shutter button. Add `CameraCapabilities` detection and expose focus/exposure/ISO/WB controls with lock toggles, gated by what the device actually supports.

**Milestone 3 — Telegram upload (happy path)**
`TelegramUploader` with `sendDocument`, manual "upload latest" test button, settings fields for token/chat ID.

**Milestone 4 — Upload queue + persistence**
`UploadQueueManager` with the full enqueue/persist/retry/backoff loop; verify a 20-shot burst queues and drains correctly without dropping the camera preview frame rate.

**Milestone 5 — Settings persistence + restore-on-relaunch**
DataStore-backed `SettingsRepository`; verify every camera/upload setting survives a full app kill and relaunch.

**Milestone 6 — Logging + status UI**
Log screen with the full stage list (Captured/Encoding/Queued/Uploading/Success/Retry/Failure); Camera screen HUD chips for queue size, upload status, Telegram connection state, images-uploaded counter.

**Milestone 7 — Hardening**
Process-death recovery testing (force-stop mid-upload, relaunch, confirm resume), Doze-mode behavior check, storage cleanup policy, edge-case device testing (LEGACY-level camera, no manual ISO support, etc.).

---

## 15. Future Improvements (Not in Scope Now)

- Optional foreground service for the upload worker if Doze/App-Standby testing shows background network throttling on your specific device.
- Batch "flush queue now" button for manually forcing immediate drain (e.g., before putting the phone to sleep).
- Simple burst-mode (hold Volume Up = rapid sequential captures) if the single-shot workflow ever needs it.
- Optional local thumbnail strip on the Camera screen for at-a-glance confirmation of the last few captures without needing to check Telegram.
- Multiple destination chats (e.g., mirror to a backup chat) if reliability requirements grow.
- Swap the flat JSON queue store for Room only if the queue depth or query needs ever grow past what a flat file comfortably handles — deliberately deferred per the "no Room unless necessary" constraint.

---

*End of design document — no code included per request. Ready to hand to an engineer (or future-you) as an implementation blueprint.*
