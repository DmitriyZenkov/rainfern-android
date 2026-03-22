## Navigation

* Start at `app/src/main/java/dev/rdime/rainfern/MainActivity.kt` -> `ui/RainfernViewModel.kt` -> `data/repository/WeatherRepository` -> touched dependency branch. 
* App wiring: `RainfernApplication.kt`, `AppContainer.kt`.
* Bug in forecast/blend/provider data: `data/repository`, `data/network/provider`, `data/aggregate`, `data/model`.
* Bug in place/location/cache/settings: `data/repository`, `data/search`, `data/location`, `data/local`.
* Bug in notifications/background refresh: `data/notification`, worker/scheduler code, `data/repository`.
* Bug in widgets: `widget`, then `data/local`, `ui` format helpers.
* Bug in UI/Compose screens: `ui/RainfernApp.kt`, `ui/RainfernViewModel.kt`, `ui/theme`, focused helper file only.
* Feature work: change ViewModel contract first, then repository/store/provider branch, then exact UI/widget surface.
* Search order: exact symbol/file name -> nearest caller -> nearest repository/store/provider -> model -> manifest/gradle only if required.
* Read `docs/architecture.md` or `docs/AGGREGATION.md` only for repo-level behavior or blend rules.
* Ignore `app/build`, `.gradle`, IDE files, generated outputs, binaries, screenshots, assets unrelated to task.
* Ignore `docs/decisions.md` unless task asks for rationale/history.
* Ignore broad `res/` scans unless task is resource/theme/widget specific.
* Ignore unrelated verticals: do not open `widget`, `data/notification`, `ui/RadarMapHtml.kt`, `data/environment` unless task points there.

## Commands

* Build app: `./gradlew :app:assembleDebug`
* Unit tests: `./gradlew :app:testDebugUnitTest`
* Single test/class: `./gradlew :app:testDebugUnitTest --tests "dev.rdime.rainfern.*Test"`
* Lint only when UI/resource/manifest/build changes: `./gradlew :app:lintDebug`
* Fastest validation path: run narrowest affected unit test; if none, run `:app:assembleDebug`; run full test suite only after cross-cutting changes.

## Constraints

* Modify only files on the active call path.
* Keep edits within one layer unless dependency changes require adjacent layer updates.
* Do not rename/move files or split large files unless requested.
* Do not rewrite `ui/RainfernApp.kt` wholesale for small UI fixes.
* Do not alter provider weighting/aggregation/model shape unless task explicitly requires forecast behavior changes.
* Preserve public data contracts, stored keys, and cache formats unless task explicitly requires migration.
* Avoid manifest/Gradle/dependency changes unless strictly necessary.

## Efficiency Rules

* Do not scan the entire repo.
* Do not read whole large files when symbol search can land on the exact block.
* Prefer targeted reads around matched symbols, call sites, and touched models.
* Stop exploring once the execution path is clear.
* Do not revisit files already ruled out.
* Do not spawn subagents.
* Do not retry identical commands.
* After a failed command, narrow scope or inspect the specific error before any rerun.
* Prefer one focused edit plus one focused validation.

## Output

* Return minimal diffs only.
* No explanations unless requested.
* Do not summarize unchanged code.
* Do not include broad cleanup, drive-by fixes, or style-only edits.
