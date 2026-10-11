# Spec Delta

## REMOVED Requirements

### Requirement: The repository source performs no update check

**Reason**: The refusal is superseded. The repository publishes the comparison basis itself — each version
slot's metadata carries a creation time, and the basemap availability manifest carries a change time per
version (`Documentation/MapRepository.md` §6) — and the app already stores the metadata of what it
installed inside the installation. What the repository source's own capability forbade is now specified as
the behavior of the `map-updates` capability, so this capability no longer states anything about comparing
an installation with the repository, and the version slots are read both to locate a database for download
and to compare it.

**Migration**: No user action and no data migration. Installed databases keep working and keep their
metadata; the update check, the update list and the non-destructive install are specified by
`map-updates`.
