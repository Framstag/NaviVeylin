$ mutation: check-metadata.sh:150 asks for the fixed window again (`gh issue list --limit 400`, helper unused)
$ sha256sum tools/issues/check-metadata.sh
26ec05afb2c6378d9c68e8cd68ca5eda1d7f24686e16fb27fd901446f97dbb07  tools/issues/check-metadata.sh
  the drift outside a 400-item window is reported: check-metadata: 400 issue(s) checked · 0 violation(s) · 0 untyped: expected '1', got '0'
FAIL metadata#theLiveReadDoesNotTruncateTheTracker
exit=1

$ restored byte-for-byte from the backup: cp <scratch>/check-metadata.sh.bak tools/issues/check-metadata.sh
$ sha256sum tools/issues/check-metadata.sh
1714e0e79ccf5f918c030f0822a267cf214f3b6b509cc22fb1a71c1b72142cf4  tools/issues/check-metadata.sh
PASS metadata#theLiveReadDoesNotTruncateTheTracker
exit=0
