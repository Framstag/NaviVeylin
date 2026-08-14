## 1. Font Cache Key Precision

- [x] 1.1 Change `FontMap` type alias in `MapPainterCairo.h` from `std::unordered_map<size_t, CairoFont>` to `std::unordered_map<double, CairoFont>`
- [x] 1.2 Update `GetFont()` in `MapPainterCairo.cpp` — remove `size_t` cast on cache lookup key, use `double` directly for `fonts.find()` and `fonts.insert()`
- [x] 1.3 Add `assert(fontSize > 0 && std::isfinite(fontSize))` guard in `GetFont()` before cache operations
- [x] 1.4 Verify build compiles with NDK for all 3 ABIs (arm64-v8a, armeabi-v7a, x86_64)

## 2. Font Cache Invalidation on Style Change

- [x] 2.1 Add font resource cleanup + `fonts.clear()` to `StyleSheetChanged()` in `MapPainterCairo.cpp` alongside existing `images.clear()`, `patterns.clear()`, `patternImages.clear()`
- [x] 2.2 Verify build compiles with NDK for all 3 ABIs

## 3. Fix Missing Font Set in DrawGlyphs (Root Cause)

- [x] 3.1 Add `CairoFont font` field to non-Pango `CairoNativeGlyph` struct in `MapPainterCairo.h`
- [x] 3.2 Store `label.font` in each glyph during non-Pango `ToGlyphs()` in `MapPainterCairo.cpp`
- [x] 3.3 Add `cairo_set_scaled_font(draw, glyph.glyph.font)` in non-Pango `DrawGlyphs()` before `cairo_show_text()`
- [x] 3.4 Verify build compiles with NDK for all 3 ABIs

## 4. Verification

- [x] 4.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=x86_64` and confirm clean build
- [x] 4.2 Run `./gradlew test` and confirm all existing unit tests pass
- [x] 4.3 Deploy to device/emulator and verify path labels render at correct size across zoom levels
- [x] 4.4 Verify path text with no explicit `size` in stylesheet renders at same scale as regular text with `size: 1.0`
