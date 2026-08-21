# wayfinder-android

A thin Android client for the **Wayfinder** strategy service. The app is
intentionally lightweight: it consumes the Wayfinder REST API, renders
server-authoritative strategy data, and forwards user observations back to
the server. **No intelligence is computed on-device.**

## Thin-client guarantees

The Android app **never** computes any of the following — they are
server-authoritative and rendered verbatim from API responses:

- `OutcomeType`
- `EvaluationStatus`
- `ConfidenceLevel`
- Predictions

When submitting an observation, the client only forwards the `type` of an
expected outcome that the server itself returned — it never invents a type.
This invariant is enforced by the DTO contract (`data/remote/Dto.kt`) and is
explicitly verified by the test suite.

## Architecture

```
┌───────────────────────────────────────────────┐
│  UI (Jetpack Compose)                         │
│  LoginScreen · StrategyScreen · OutcomeScreen │
└───────────────┬───────────────────────────────┘
                │  StateFlow
┌───────────────┴───────────────────────────────┐
│  ViewModels (LoginViewModel, StrategyViewModel,│
│               OutcomeViewModel)               │
└───────────────┬───────────────────────────────┘
                │  Result<T>
┌───────────────┴───────────────────────────────┐
│  Repositories (Auth, Strategy, Profile)       │
└───────────────┬───────────────────────────────┘
                │  suspend fun
┌───────────────┴───────────────────────────────┐
│  WayfinderApi (Retrofit) + AuthInterceptor    │
│  TokenStorage (EncryptedSharedPreferences)    │
└───────────────────────────────────────────────┘
```

## Server contract

- **Base URL:** `https://my-project-wheat-omega-90.vercel.app`
- **Auth (P4.0):**
  - `POST /api/auth/credentials` — email + password → `{ accessToken, refreshToken, user }`
  - `POST /api/auth/refresh` — `{ refreshToken }` → `{ accessToken, refreshToken }`
  - `POST /api/auth/logout`
- **Protected endpoints** (Bearer token):
  - `GET /api/profile`
  - `GET /api/strategy/adopt`
  - `GET /api/strategy/{id}/explanation`
  - `GET /api/strategy/{id}/outcomes`
  - `POST /api/strategy/{id}/outcome`
  - `GET /api/strategy/history`
  - `GET /api/actions`
  - `POST /api/actions/{id}/outcome`
- **Error contract:** `{ error: { code, message, requestId } }`
  - Codes: `AUTH_REQUIRED`, `AUTH_EXPIRED`, `AUTH_REFRESH_INVALID`,
    `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`,
    `RATE_LIMITED`, `SERVER_ERROR`, `SERVICE_UNAVAILABLE`

### Demo credentials

```
email:    demo-user@wayfinder.app
password: wayfinder
```

The login screen ships with the demo email pre-filled.

## Token storage

Access and refresh tokens are persisted via
`androidx.security.crypto.EncryptedSharedPreferences`, which uses AES-256
under the Android Keystore. Tokens are **never** written to plain
SharedPreferences and **never** appear in logs — OkHttp logging is set to
`BASIC` (method, URL, and status code only; never headers or bodies).

This is verified by `TokenLifecycleTest.http_logging_at_BASIC_level_never_emits_the_access_token`,
which reconstructs the production logging configuration, makes a real HTTP
call whose request carries a Bearer access token, captures every line the
logger writes, and asserts that the token value never appears.

### Auth interceptor behavior

`AuthInterceptor` (see `data/remote/AuthInterceptor.kt`):

1. Skips auth for `/api/auth/credentials` and `/api/auth/refresh`.
2. Attaches `Authorization: Bearer <accessToken>` to every other request.
3. On `401`:
   - Acquires a single-flight refresh lock so parallel failures don't
     trigger N refresh calls.
   - Calls `/api/auth/refresh` via a plain OkHttp client (no auth
     interceptor, to avoid recursion).
   - On success: retries the original request once with the new token.
   - On failure: clears local tokens and replays the original request
     without a token so callers see the server's structured 401.

## Build

Requirements:
- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- Gradle 8.9 (wrapper jar is checked in)

```bash
./gradlew assembleDebug        # build debug APK
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # instrumentation tests (needs an emulator)
```

The debug APK is written to `app/build/outputs/apk/debug/`.
The androidTest APK is written to `app/build/outputs/apk/androidTest/debug/`.

## Tests

### JVM unit tests (`app/src/test/`)

- **`DtoParsingTest`** — verifies DTOs parse the server's JSON contract using
  MockWebServer. Covers `MobileLoginResponse`, `MobileApiError` (direct and via
  envelope), `StrategyDTO`, `OutcomesDTO`, and resilience to unknown fields.
  Asserts that opaque server strings (`OutcomeType`, `EvaluationStatus`,
  `ConfidenceLabel`) are preserved verbatim — never parsed or interpreted.
- **`ErrorMappingTest`** — verifies all ten server error codes map 1:1 to the
  client `ErrorCode` enum, that every code exposes a non-blank user-visible
  message that never leaks tokens or request IDs, and that `Throwable` →
  `WayfinderError` mapping handles `HttpException`, `IOException`, and generic
  throwables correctly.
- **`TokenLifecycleTest`** — verifies the storage/retrieval/clear lifecycle
  against the `InMemoryTokenStorage` implementation, and that the production
  HTTP logging configuration (BASIC level) never leaks access or refresh
  tokens into logs. Includes a sanity check proving that HEADERS-level logging
  *would* leak the token — so the BASIC-level assertion can never silently
  become vacuous.

### Instrumentation tests (`app/src/androidTest/`)

- **`SmokeTest`** — launches `MainActivity` and verifies:
  1. The login screen renders with email field, password field, and sign-in
     button (assertions on `assertIsDisplayed`).
  2. The demo email is pre-filled and the password starts blank, so the
     sign-in button is initially **disabled** (`assertIsNotEnabled`).
  3. Typing a password **enables** the button (`assertIsEnabled`).
  4. Tapping sign-in attempts an API connection — the app either navigates
     forward to the strategy screen or surfaces a user-visible error message.
  5. The instrumentation target package is `com.wayfinder.android`.

  These are real assertions on observable UI state — not just node existence
  checks.

## CI

`.github/workflows/android-build-and-test.yml` runs on every push and PR:

1. Check out, set up JDK 17, set up Android SDK (API 34 + build-tools 34.0.0).
2. **Verify** the SDK is present: `ANDROID_HOME`, `platforms/android-34`,
   `build-tools/34.0.0`, `adb`, `emulator`, `sdkmanager`.
3. **Print** tool versions: `sdkmanager --version`, `adb version`,
   `java -version`, `./gradlew --version`.
4. `./gradlew assembleDebug assembleDebugAndroidTest` — build the debug APK
   and the androidTest APK.
5. `./gradlew test` — fails if `tests_executed == 0`.
6. Create AVD `test_avd` (API 34, x86_64).
7. Start the emulator in the background.
8. `adb wait-for-device`.
9. Wait for `sys.boot_completed == 1` with a 300-second timeout — fail if
   the emulator doesn't boot.
10. Print `ro.build.version.sdk` and `ro.product.cpu.abi`.
11. `./gradlew connectedAndroidTest` — fails if `tests_executed == 0`.
12. **Upload artifacts**: debug APK, androidTest APK, unit + instrumentation
    JUnit XML, logcat, emulator log, Gradle HTML reports.

## Project layout

```
app/
  src/main/
    AndroidManifest.xml
    java/com/wayfinder/android/
      MainActivity.kt
      core/
        Result.kt              # Result<T>, WayfinderError, ErrorCode
        WayfinderApp.kt        # Application; wires TokenStorage + WayfinderApi
      data/
        local/
          TokenStorage.kt      # EncryptedSharedPreferences + InMemory impl
        remote/
          Dto.kt               # all DTOs with Moshi annotations
          WayfinderApi.kt      # Retrofit interface
          AuthInterceptor.kt   # Bearer token + single-flight refresh+retry
          ApiModule.kt         # OkHttp + Retrofit factory (BASIC logging)
          ErrorMapping.kt      # Throwable → WayfinderError
        repository/
          AuthRepository.kt
          StrategyRepository.kt
          ProfileRepository.kt
      feature/
        auth/      LoginViewModel.kt, LoginScreen.kt
        strategy/  StrategyViewModel.kt, StrategyScreen.kt
        outcome/   OutcomeViewModel.kt, OutcomeScreen.kt
      ui/theme/    Color.kt, Type.kt, Theme.kt
    res/values/    strings.xml, themes.xml, colors.xml
    res/mipmap-anydpi-v26/  ic_launcher.xml, ic_launcher_round.xml
    res/drawable/  ic_launcher_foreground.xml
  src/test/java/com/wayfinder/android/
    DtoParsingTest.kt
    TokenLifecycleTest.kt
    ErrorMappingTest.kt
  src/androidTest/java/com/wayfinder/android/
    SmokeTest.kt
  proguard-rules.pro
gradle/wrapper/
  gradle-wrapper.properties   # Gradle 8.9
  gradle-wrapper.jar
gradlew, gradlew.bat
.github/workflows/android-build-and-test.yml
```

## License

Proprietary — Wayfinder.
