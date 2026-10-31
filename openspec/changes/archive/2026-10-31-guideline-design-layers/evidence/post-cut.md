# Post-cut size table (ruler: `grep -b '^## '` byte offsets; `bash tools/check-doc-routes.sh --sizes` agrees)

## guidelines/Design.md — 28438 B

```
   1639 B   1.6 kB  ## 1. Module layering
    261 B   0.3 kB  ## 2. Stack & UI patterns
   4773 B   4.8 kB  ## 3. ViewModel & state
  10757 B  10.8 kB  ## 4. Threading
    130 B   0.1 kB  ## 5. Native boundary
     65 B   0.1 kB  ## 6. Rendering pipeline
     64 B   0.1 kB  ## 7. Follow mode & GPS
    192 B   0.2 kB  ## 8. Android Auto & cross-variant
     66 B   0.1 kB  ## 9. Data, persistence & search
     64 B   0.1 kB  ## 10. Build & release
     56 B   0.1 kB  ## 11. Testing
   8801 B   8.8 kB  ## 12. General engineering principles
    136 B   0.1 kB  ## 13. Superseded decisions
```

## guidelines/DesignStack.md — 7673 B

```
   3443 B   3.4 kB  ## 2. Stack & UI patterns
   3519 B   3.5 kB  ## 9. Data, persistence & search
```

## guidelines/DesignNative.md — 14443 B

```
    221 B   0.2 kB  ## 5. Native boundary
   7975 B   8.0 kB  ## 5a. The boundary's shape and its JNI contract
   5533 B   5.5 kB  ## 5b. The bridge's two sides, the database-open asymmetry and the submodule discipline
```

## guidelines/DesignCrossVariant.md — 25432 B

```
   1945 B   1.9 kB  ## 6. Rendering pipeline
   1090 B   1.1 kB  ## 7. Follow mode & GPS
    213 B   0.2 kB  ## 8. Android Auto & cross-variant
  10013 B  10.0 kB  ## 8a. The car host path and the surface
   2946 B   2.9 kB  ## 8b. The screen-observation seam, cross-variant parity and distribution
    733 B   0.7 kB  ## 10. Build & release
   7710 B   7.7 kB  ## 11. Testing
```

Companions: Design.why.md 7401 B, Design.recipes.md 2916 B, DesignNative.why.md 1589 B.

Heading-number union over the four normative documents:
```
1 10 11 12 13 2 3 4 5 5a 5b 6 7 8 8a 8b 9
```
Baseline set 1..13 plus the sub-sections §5a §5b §8a §8b — no number moved.
