# Profit Driving — Session Summary

## Completed Work

### Redesign Simplified Ride Card
- **item_ride_card.xml** — Applied design token system (use_color, text_primary, text_secondary, etc.), icon+suffix pattern, dynamic income display

### Dialog Button Contrast Fix
- **themes.xml** — Created `Widget.CorridaCerta.DialogButton` (+ Negative variant) with explicit `parent="Widget.AppCompat.Button.Borderless"`; set `alertDialogTheme` to `ThemeOverlay.CorridaCerta.Dialog`
- **themes.xml** — Added `textColorAlertDialogListItem` override to dialog theme overlay
- **dialog_actions.xml** — Created custom layout for MyDayRideAdapter dialog items
- **MyDayRideAdapter.kt** — Switched from platform to AppCompat AlertDialog, uses custom dialog layout

### Redundant Expense Flow
- **AddExpenseDialog.kt** — Added `type: CostType?` parameter to pre-select expense category, skipping the type picker when context already determines the category

### "custo/min" → "custo/minuto"
- **SummaryService.kt** — Corrected string display from "custo/min" to "custo/minuto"

### Configurable Animations for Floating Card
- **AnimationConstants.kt** — `AnimationConfig` data class with field-level durations, made all animation durations configurable via `AnimationConfig`
- **PreferenceManager.kt** — Added `animationSpeed` preference (default: 1.0, range: 0.2–3.0), `get/setAnimationSpeed()`
- **FloatingCardService.kt** — Applied `AnimationConfig` to card animate-in/out, swipe-to-dismiss, and selection transitions
- **SettingsActivity.kt** — Added "Velocidade das Animações" row with spinner (0.5x–2.0x)
- **strings.xml** — Added `animation_speed_*` entries

### Multi-Tap on Floating Bubble
- **FloatingBubbleService.kt** — Single tap: open app; double tap: trigger re-scan of current Uber/99 screen via `RideAccessibilityServiceV2.triggerReScan()`; triple tap: show last ride card from DB via FloatingCardService
- **RideAccessibilityServiceV2.kt** — Added `instance` companion reference and `triggerReScan()` public method
- **DatabaseHelper.kt** — Added `getLastRide(): RideRecord?` method
- **FloatingCardService.kt** — Added `SHOW_LAST_RIDE` action handler

### OCR para 99: ML Kit → Tesseract (tess-two)
- **build.gradle** — Substituiu `com.google.mlkit:text-recognition:16.0.0` por `com.rmtheis:tess-two:9.1.0`
- **RideAccessibilityServiceV2.kt** —
  - Removeu todos imports de ML Kit (`TextRecognition`, `InputImage`, `Tasks`, etc.)
  - `captureAndProcess99Card()`: callback-based, screenshot → Tesseract → texto → `App99Extractor.createFromTexts()`
  - `captureScreen()`: novo método simplificado para Uber (screenshot + save, sem OCR)
  - SDK guard: `Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE` → early return
- **App99Extractor.kt** — `extractWithOCR()` usando `TessBaseAPI` do tess-two, init com "por", extrai texto; `createFromTexts()` faz parsing do texto OCR
- **Build compila** `compileDebugKotlin` → **SUCCESS**

### Badge System: Círculos por App (Uber/99)
- **Removido** `badge_app.xml` (rounded rect com `@color/primary`)
- **Criado** `bg_99_circle.xml` — círculo amarelo (#FFDD00) para badge 99
- **Criado** `bg_uber_circle.xml` — círculo preto (#000000) para badge Uber
- **colors.xml** — Adicionado `app_99_text` (#000000) e `app_uber_text` (#FFFFFF)
- **item_ride_card.xml** — Removeu `android:background` e `android:textColor` (agora dinâmico via código)
- **item_my_day_ride.xml** — Mesma limpeza
- **MainActivity.kt** (HistoryAdapter) — `setBackgroundColor()` → `setBackgroundResource()` + `setTextColor()`
- **MyDayRideAdapter.kt** — Mesma mudança

### 99 Icon: Cor e Paths
- **ic_99.xml** — Fundo alterado de #FF6600 (laranja) para #FFDD00 (amarelo); "99" alterado de stroke branco para fill preto com paths simplificados
- **colors.xml** — `app_99` atualizado de #FF6600 para #FFDD00

### App Reading Toggles (Settings)
- **SettingsActivity.kt** — Card "Leitura de Apps" com toggles Uber/99; toggle 99 disabled em API < 34
- **activity_settings.xml** — Card com ImageView logos + SwitchCompat
- **ic_99.xml** / **ic_uber.xml** — SVGs para os logos nos toggles
- **RideAccessibilityServiceV2.kt** — `isAppReadingEnabled()` check antes de processar cada app

### Database CREATE_TABLE Missing Columns
- **DatabaseHelper.kt** — `CREATE_TABLE` estava faltando `pickup_address`, `dropoff_address`, `card_hash` (adicionados via migrações v12/v19, mas nunca incorporados ao schema inicial). Causava crash em instalação limpa: `no such column: dropoff_address` ao criar índice `idx_dropoff_address` em `onCreate()`.

### DataExporter Import Fix (code 0 sqlite_ok)
- **DataExporter.kt** — Fixed 3 runtime errors in `import()`:
  1. **PRAGMAs via `execSQL`** → `rawQuery().close()` — `PRAGMA journal_mode = WAL` returns data (the journal string), `execSQL` calls `executeUpdateDelete()` which throws SQLiteException code 0. Changed all 3 PRAGMAs (`busy_timeout`, `journal_mode`, `foreign_keys`) to `rawQuery().close()`.
  2. **Nullable values in `ContentValues.put(key, nullable)`** — All import methods (`importRides`, `importRefuels`, `importExpenses`, etc.) now use `cv.put(key, o.optXxx("k", default))` with non-null typed defaults (e.g. `o.optDouble("d", 0.0)` instead of `putOpt(o.optDoubleOrNull("d"))`). Avoids `Hashtable.put(Object, Object)` fallback on older ContentValues typed methods.
  3. **Removed undefined `TABLE_RIDES_COLS` constants** — My previous edit referenced `TABLE_RIDES_COLS.size` etc. which don't exist; replaced with `ContentValues()` default constructor.
- **Removed unused `ContentValues.putOpt` extension function** — All import functions now use explicit typed `put` calls.

## Key Files Modified
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/layout/dialog_actions.xml`
- `app/src/main/res/layout/item_ride_card.xml`
- `app/src/main/res/layout/item_my_day_ride.xml`
- `app/src/main/res/layout/activity_monthly_stats.xml`
- `app/src/main/res/layout/activity_expenses.xml`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/drawable/bg_hero_card_dark.xml`
- `app/src/main/res/drawable/bg_table_header.xml`
- `app/src/main/res/drawable/bg_btn_add_outline.xml`
- `app/src/main/res/drawable/bg_99_circle.xml` (new)
- `app/src/main/res/drawable/bg_uber_circle.xml` (new)
- `app/src/main/res/drawable/ic_99.xml` (rewritten)
- `app/src/main/res/drawable/ic_uber.xml` (new)
- `app/src/main/java/com/profitdriving/MyDayRideAdapter.kt`
- `app/src/main/java/com/profitdriving/AddExpenseDialog.kt`
- `app/src/main/java/com/profitdriving/ExpensesActivity.kt`
- `app/src/main/java/com/profitdriving/SettingsActivity.kt`
- `app/src/main/java/com/profitdriving/FloatingBubbleService.kt`
- `app/src/main/java/com/profitdriving/FloatingCardService.kt`
- `app/src/main/java/com/profitdriving/SummaryService.kt`
- `app/src/main/java/com/profitdriving/DatabaseHelper.kt`
- `app/src/main/java/com/profitdriving/PreferencesManager.kt`
- `app/src/main/java/com/profitdriving/AnimationConstants.kt`
- `app/src/main/java/com/profitdriving/MainActivity.kt`
- `app/src/main/java/com/profitdriving/CaptureManager.kt`
- `app/src/main/java/com/profitdriving/accessibility/RideAccessibilityServiceV2.kt`
- `app/src/main/java/com/profitdriving/accessibility/extractor/App99Extractor.kt`
- `app/src/main/java/com/profitdriving/accessibility/extractor/UberCardExtractor.kt`
- `app/src/main/java/com/profitdriving/accessibility/extractor/CardType.kt`
- `app/src/main/java/com/profitdriving/parser/App99CardParser.kt`
- `app/src/main/java/com/profitdriving/models/WorkProfile.kt`
- `app/src/main/java/com/profitdriving/WorkProfileCalculator.kt`
