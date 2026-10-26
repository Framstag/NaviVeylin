# Evidence — issue #305, the Android feature set becomes features.cmake's

| file | what it is, and how it was taken |
|---|---|
| `head-feature-set.txt` | the four headers HEAD's app-side block rendered into the build tree (`app/.cxx/Debug/4z1b2r3y/arm64-v8a/osmscout-features/`): marisa defined, every other feature undef — the red state, read 2026-10-26 |
| `feature-headers-after.txt` | the same four, rendered by `cmake/features.cmake` into `libosmscout/libosmscout-*/include/…` after the change: FreeType, cairo-ft defined, Pango and Fontconfig undef |
| `include-path-and-link-line.txt` | `grep -c osmscout-features build.ninja` = 0 (no app-rendered header directory on any include path), and the map library's `LINK_LIBRARIES` with the freetype closure behind `libfreetype.a` |
| `nm-freetype-symbols.txt` | `nm -C --defined-only libosmscout_mapd.so \| grep " FT_"` — the FreeType entry points the map library calls are linked into the library |
| `configure-arm64.log` | the arm64-v8a CMake configure (`cmake .` in the Gradle build tree, the cache a HEAD configure left) |
| `native-build-arm64.log` | `ninja OSMScout OSMScoutClient OSMScoutMap OSMScoutMapCairo naviveylin_log_bridge osmscout_client_java` — green |
| `link-before-closure.log` | the same link with the freetype closure guard forced off: `undefined symbol: png_create_read_struct`, `BZ2_bzDecompress`, `inflate`, `BrotliDecoderDecompress` |
| `falsified-cairo-ft-cache.txt` | guard removed + cache entry forced empty → `/* #undef OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT */` |
| `restored-cairo-ft-cache.txt` | guard restored + the same forced-empty entry → `#define OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` |
| `gate.log` | the change's gate (`tools/loop/gate.sh --base 7856a9d1`) |
