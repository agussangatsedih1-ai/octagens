# OctaGens Changelog

## [1.0.6-BETA] - 2026-11-14

### Added
- Added a dedicated `/og sellwand shop` GUI for purchasing configured Sellwand tiers.
- Added per-Sellwand shop settings: enable/disable, price, and currency.
- Added separate booster configuration files for Generator Drop, Sell Multiplier, and MCu Gain.
- Added selectable booster multiplier presets and custom whole-number multipliers from 1 to 10.
- Added selectable giveaway durations (1m, 5m, 10m, 30m, 1h, 8h, 12h, and 24h); duration selection is free.
- Added a decorated booster selection flow with glass-pane fillers, themed icons, and detailed lore.
- Added dedicated booster automatic-schedule and chat-reward configuration files.
- Added prefixed, organized help entries for player commands, MPC shortcuts, daily missions, and Sellwand shop.

### Fixed
- Fixed the missing `Fmt` utility import in `BoosterManager`, which caused compilation errors at the booster formatting call sites.

### Changed
- Updated project version from `1.0.6-SNAPSHOT` to `1.0.6-BETA`.
- Organized bundled configuration resources so only `config.yml` and `plugin.yml` remain at the resources root; feature settings are grouped into folders.
- Giveaway prices now use the configured Denari price per 1.0x multiplier: Generator Drop 10,000, Sell Multi 12,000, and MCu Gain 15,000 by default.
- Booster duration no longer changes the price.
- Custom multiplier input accepts integers only, between 1 and 10.
- Weekend Generator Drop multiplier now rolls a decimal value in 0.1x steps between the configured minimum and maximum on each drop.
- Added migration of legacy root `boosters.yml` and `daily-missions.yml` settings into their new folders.
- Standardized prefix display for multiline command messages.

### Configuration
- `boosters/automatic.yml` controls automatic booster schedules.
- `boosters/gen-drop.yml`, `boosters/sell-multi.yml`, and `boosters/mcu-gain.yml` control each giveaway shop.
- `boosters/chat-rewards.yml` controls chat phrases, rewards, and giveaway notifications.
- `missions/daily-missions.yml` contains Daily Mission settings.
- `sellwand/sellwand.yml` contains Sellwand definitions and each tier's shop price/currency.

### Notes
- This is a BETA source package. A full Maven compile and live-server test are still required before production use.
