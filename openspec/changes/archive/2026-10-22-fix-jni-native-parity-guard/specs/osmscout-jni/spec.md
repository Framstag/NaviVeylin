# osmscout-jni — deltas

## ADDED Requirements

### Requirement: The override mirrors the submodule's natives, except the recorded deviations

The Android override of each of the five overridden bridge files SHALL declare a native with the same name and
parameter types as every native the submodule's copy declares, except a divergence recorded in
`osmscout-client-java/native-parity.txt` with its file, direction and reason. The two sides SHALL differ by
exactly that record — in both directions — and a recording the trees no longer need SHALL fail as well. The
build gate `:app:checkNativeParity`, wired into `preBuild`, SHALL assert it.

#### Scenario: An unrecorded submodule native is refused

- **WHEN** the submodule declares a native (e.g. `setGpsMarker(double,double,double,double)`) that the override
  does not declare, and `osmscout-client-java/native-parity.txt` carries no line for it
- **THEN** the scan reports one finding naming the native, the override file that lacks it and the exact list
  line that would record it, and the same fixture with that line recorded reports none
- **Case**: `buildSrc: NativeParityScannerTest` "a new submodule native without a record is a finding"

#### Scenario: An unrecorded Android addition is refused

- **WHEN** the override declares a native (e.g. `getMaxSpeedAt(double,double)`) that the submodule does not, and
  the list carries no line for it
- **THEN** the scan reports one finding naming the added native and the submodule file that would lose it on the
  next sync
- **Case**: `buildSrc: NativeParityScannerTest` "a new override native without a record is a finding"

#### Scenario: A recording the trees no longer need is refused

- **WHEN** the list records a divergence (e.g. `searchLocations(String,int,String,long)`) that both sides now
  agree on
- **THEN** the scan reports one finding naming the stale line and saying to remove it
- **Case**: `buildSrc: NativeParityScannerTest` "a recorded deviation that no longer deviates is a finding"

#### Scenario: A malformed recording is refused

- **WHEN** a list line has three fields, an unknown direction, a file outside the five overridden ones, no
  parenthesised signature, or no reason
- **THEN** the scan reports one finding per malformed line, naming the list and the line number, so no
  allowance drops silently
- **Case**: `buildSrc: NativeParityScannerTest` "a malformed deviation line is a finding"

#### Scenario: A package qualifier and prose are not divergences

- **WHEN** the same declaration reads `java.nio.ByteBuffer` on one side and `ByteBuffer` on the other, or a
  javadoc or a `//` comment *shows* a signature
- **THEN** the two sides compare equal and the prose declares nothing
- **Case**: `buildSrc: NativeParityScannerTest` "package qualifiers and prose are not divergences"

#### Scenario: The app build registers the parity gate and wires it into preBuild

- **WHEN** the app build script is read as the project's own file
- **THEN** it registers `checkNativeParity` over the two Java sides and the recorded list, and makes `preBuild`
  depend on it, so the scan runs in every app build instead of waiting for the call site's `NoSuchMethodError`
- **Case**: `app: NativeOverrideParityGuardTest` "the app build registers the parity gate and wires it into preBuild"

#### Scenario: The recorded deviations are the tree's own

- **WHEN** the recorded list is read as the project's own file
- **THEN** every line carries four fields with a reason and one of the five overridden files, and the list
  holds exactly the divergences the two trees carry — five submodule-only, five override-only
- **Case**: `app: NativeOverrideParityGuardTest` "the recorded deviations are the tree's own"
