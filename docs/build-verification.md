# Build and verification guide

This guide captures the baseline build setup for FinanzApp. Keep it current
whenever the Android Gradle Plugin, Gradle wrapper, Kotlin, Java, or Android SDK
versions change.

## Quality principle

Every change must protect two priorities together:

- Financial reliability: balances, debts, payments, categories, privacy, and
  auditability must remain correct and explainable.
- UX/UI clarity: users must understand what is exact, estimated, pending review,
  or unsafe before the app changes financial state.

Do not improve one of these at the expense of the other. A financially correct
feature that confuses users can still cause bad money decisions; a polished UI
that hides uncertainty can be worse than no feature.

## Required local tools

- Windows PowerShell.
- Android Studio or a JDK 21 installation.
- Android SDK with the compile SDK used by the project.
- Gradle wrapper from this repository: `.\gradlew.bat`.

The current project compiles with Java 21. If `JAVA_HOME` is not configured but
Android Studio is installed in the default location, use its bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Check the active Gradle/JDK pairing:

```powershell
.\gradlew.bat --version
```

Expected baseline observed during OPS-000:

- Gradle: `9.5.1`
- Launcher JVM: JetBrains Java `21.0.10`
- Android Gradle Plugin: `9.2.1`
- Kotlin plugin: `2.1.0`
- App JVM target: `21`

## Baseline verification commands

Run these before starting financial logic work and before opening a PR:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

If all dependencies are already cached, the same checks can be run offline:

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline
.\gradlew.bat :app:testDebugUnitTest --offline
.\gradlew.bat :app:lintDebug --offline
```

Note: `lintDebug --offline` may fail the first time if
`com.android.tools.lint:lint-gradle` is not cached. Run `lintDebug` once without
`--offline` to populate the cache.

## Current baseline results

Observed on the OPS-000 branch:

- `:app:compileDebugKotlin --offline`: success.
- `:app:testDebugUnitTest --offline`: success, but `NO-SOURCE` because there are
  no unit tests yet.
- `:app:lintDebug --offline`: failed only because the lint tool was not cached.
- `:app:lintDebug`: success after dependency download.

Lint result summary:

- `0 errors`
- `34 warnings`
- `2 hints`

Important non-blocking warnings to address later:

- `targetSdk = 36` is below the latest available SDK in this environment.
- Gradle `9.6.0` is available while the wrapper uses `9.5.1`.
- Some dependencies have newer versions.
- `mipmap-anydpi-v26` is unnecessary because `minSdk = 26`.
- Compose hints recommend primitive state APIs for some `Long` state values.

Gradle configuration warnings:

- `android.builtInKotlin=false` is deprecated and will be removed in AGP 10.
- `android.newDsl=false` is deprecated and will be removed in AGP 10.
- Some plugin code is still using obsolete variant APIs.

These warnings do not block the current build, but they should be tracked before
the project moves toward production quality.

## PR checklist

For every PR, include:

- Commands executed and results.
- Whether tests are absent, added, or changed.
- Financial invariants protected by the change.
- UX/UI impact, including wording for estimated or uncertain values.
- Screenshots for visible UI changes.
- Migration notes if Room schema changes.

