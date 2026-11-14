# Changelog

All notable changes to OctaGens are documented here.

## [1.0.6] - 2026-11-14

### Improved
- Moved all Daily Missions GUI layout and visual settings into `guis/dailymissions.yml`.
- Moved all Sellwand Shop GUI layout, decoration, product lore, close button, and six-row sizing into `guis/sellwandshop.yml`.
- Moved all Giveaway Booster GUI layout, stage titles, item names, lore, button slots, frames, and decorations into `guis/booster.yml`.
- Made the Daily Missions reminder and reset notifications use a clickable `[Missions]` component that opens `/dailymissions`.
- Reworked Daily Missions presentation with configurable progress, completion, claim, and reward sections.
- Converted reward IDs such as `VANILLA:IRON_INGOT:8` into readable item descriptions such as `8x Iron Ingots`; MMOItems rewards use their configured display name when available.
- Removed the repeated plugin prefix from `/og help` and `/og help-admin`, while retaining prefixes for normal command feedback.
- Reload now closes every OctaGens-owned menu before reloading its configuration, so players do not keep stale inventories open.
- Reload now reloads Sellwand Shop GUI configuration alongside the other GUI files.

### Fixed
- Daily Mission reward lore no longer prints raw reward definition strings.
- Daily mission notification link text is rendered as an interactive chat component, not a plain `/dailymissions` instruction.
- GUI styling tags are passed through the plugin's MiniMessage-to-legacy formatting path before being placed in item metadata.

### Configuration
- New GUI files: `guis/dailymissions.yml`, `guis/sellwandshop.yml`, and `guis/booster.yml`.
- Existing mission definitions remain in `missions/daily-missions.yml`; player progress remains stored separately.
- Existing Sellwand prices and availability remain in `sellwand/sellwand.yml`.

### Verification status
- Source-level configuration and structural validation performed.
- A full Maven build and in-server interaction test are still required before declaring the release production-ready.
