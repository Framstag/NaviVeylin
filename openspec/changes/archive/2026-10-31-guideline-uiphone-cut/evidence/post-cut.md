# Post-cut size table

`bash tools/check-doc-routes.sh --sizes | grep "section-cost UIPhone"`

```
check-doc-routes: section-cost UIPhone.md §5 1203 bytes 18 lines routed
check-doc-routes: section-cost UIPhone.md §6a 6133 bytes 78 lines routed
check-doc-routes: section-cost UIPhone.md §12 2094 bytes 26 lines routed
check-doc-routes: section-cost UIPhone.md §13 655 bytes 9 lines routed
check-doc-routes: section-cost UIPhone.md §14 763 bytes 11 lines routed
check-doc-routes: section-cost UIPhone.md §15 975 bytes 14 lines routed
check-doc-routes: section-cost UIPhone.md §16 1041 bytes 15 lines routed
check-doc-routes: section-cost UIPhone.md §17 985 bytes 16 lines routed
check-doc-routes: section-cost UIPhone.md §18 4503 bytes 53 lines routed
check-doc-routes: section-cost UIPhone.md §19 289 bytes 5 lines routed
check-doc-routes: section-cost UIPhoneMap.md §7 1704 bytes 25 lines routed
check-doc-routes: section-cost UIPhoneMap.md §7a 3393 bytes 43 lines routed
check-doc-routes: section-cost UIPhoneMap.md §7b 7978 bytes 91 lines routed
check-doc-routes: section-cost UIPhoneMap.md §8 1078 bytes 17 lines routed
check-doc-routes: section-cost UIPhoneMap.md §8b 3301 bytes 41 lines routed
check-doc-routes: section-cost UIPhoneMap.md §8c 3493 bytes 44 lines routed
check-doc-routes: section-cost UIPhoneMap.md §8a 1869 bytes 27 lines routed
check-doc-routes: section-cost UIPhoneMap.md §10a 2413 bytes 35 lines routed
check-doc-routes: section-cost UIPhoneMap.md §11 3403 bytes 43 lines routed
```

`wc -c guidelines/UIPhoneMap.md guidelines/UIPhone.md`
```
29340 guidelines/UIPhoneMap.md
19375 guidelines/UIPhone.md
48715 insgesamt
```

Both under 32 000 B; largest section §7b 7 978 B (< 12 000). Heading-number union
(both documents) = `5, 6a, 7, 7a, 7b, 8, 8a, 8b, 8c, 10a, 11, 12, 13, 14, 15, 16, 17, 18, 19` — equals the baseline set, no number moved.
