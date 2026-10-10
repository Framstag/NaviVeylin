<!-- cachekey: e774a057ea5473f30274228f61f8fe18e636128e0ec4cdfb574c73fbb927a3fe area: map-interaction -->
- **Pinch to zoom, map keeps up** — Pinch to zoom and the map scales immediately, keeps the point under the cursor or the vehicle marker fixed, and lands as the full render with no jump. Buttons, the scroll wheel and auto-zoom ease in over 200-300 ms, follow mode keeps running, and further input retracks without a snap.
  <!-- cap: adaptive-zoom#Render timing metrics -->
  <!-- cap: adaptive-zoom#Zoom placeholder from scaled buffer -->
  <!-- cap: smooth-zoom#Animation continues until native render completes -->
  <!-- cap: smooth-zoom#Eased zoom animation on discrete zoom input -->
  <!-- cap: smooth-zoom#Geographic anchor stays fixed during zoom animation -->
  <!-- cap: smooth-zoom#Retracking on rapid zoom input -->
  <!-- cap: smooth-zoom#Viewport records final magnification -->
  <!-- cap: smooth-zoom#Zoom animation composes with follow mode -->
  <!-- cap: smooth-zoom#Zoom animation never exposes uncovered map area -->
  <!-- cap: zoom-transition-scaling#Native render replaces placeholder at exact target magnification -->
  <!-- cap: zoom-transition-scaling#Placeholder origin matches geographic anchor -->
  <!-- cap: zoom-transition-scaling#Zoom placeholder uses continuous scale factor -->
