# Changelog

All notable changes to Math Mayhem are documented here. The project follows
semantic versioning for its own releases.

## 1.0.1 — 2026-11-12

### Added

- Title-screen **Math Mayhem** button and F6 access from the title screen and
  pause menu.
- **Safe Off** button in the configuration panel.
- Automatic recovery from a recent worldgen crash: aggressive settings are
  reset and tampering is disabled on the next launch.
- Red warning in the panel when scale/offset/jitter are outside the stable range.
- `-Dmathmayhem.safe=true` launch property for forced safe mode.
- Gitea Actions build workflow, contribution guide, changelog, editor config,
  and Git attributes.

### Changed

- F7 now performs a full safe-off (resets dangerous values) instead of only
  changing the mode to Off.
- All game math mode now excludes structure, feature, and placement classes to
  avoid chunk-boundary crashes.
- The configuration screen returns to its parent screen (title/pause) on close.

### Fixed

- Panel could not be opened from the main menu, leaving players locked out after
  a world-breaking setting.
- A crash during feature placement could leave aggressive settings persisted for
  the next launch.

## 1.0.0 — 2026-11-12

### Added

- Initial Fabric 26.3 client mod.
- ASM JVM agent rewriting `FADD`, `DADD`, `FMUL`, `DMUL`, `FDIV`, and `DDIV`.
- Worldgen Mixin fallback for `PerlinNoise` and `SimplexNoise`.
- Live configuration screen with scale, offset, operation filters, jitter, and
  NaN guard.
- Client-only keybindings and command-independent configuration.
