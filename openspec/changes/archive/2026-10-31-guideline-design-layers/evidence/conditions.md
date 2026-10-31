# Condition counts, before and after

Command: for id in document-size routed-section-size normative-purity; ... `bash tools/sensors/metrics/doc-context.sh` with SENSORS_CONDITIONS='[]'.

```
before:  document-size 1 (Design)   routed-section-size 2 (Design §5, Design §8)   normative-purity 7 (Design 4, FeatureList 2, Logging 1)
after:   document-size 0            routed-section-size 0                          normative-purity 3 (FeatureList 2, Logging 1)
```
No purity condition for any Design-family normative document remains.
