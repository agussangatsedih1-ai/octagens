# OctaGens 1.0.6

## 1.0.6 additions

- Per-player snapshot files are written to `player-data/<uuid>.yml`; the existing SQL database remains authoritative for Magicule pools and placed-block records.
- Administrative commands: `/octagens reset energy <player>` and `/octagens set energylevel <player> <level>` (OP by default).
- Built-in Sellwand placeholders include `{id}`, `{symbol}`, `{uses}`, `{multiplier}`, `{cooldown}`, `{player}` and SuperiorSkyblock island placeholders. Per-wand custom placeholders are configured under `placeholders:` in `sellwand/sellwand.yml`.
- `/mpc`, `/mpc upgrade`, `/mpc takeall`; Shift + right-click on an owned Magicule Power Collector opens the Magicule upgrade/shop screen.
- `/dailymissions` with reset at 00:00 Asia/Jakarta, progress persistence, playtime missions and configurable item/command rewards.
- Server-wide DROP, SELL and MCU boosters, daily random sell/MCu automatic multipliers, booster giveaway shop and chat-phrase rewards.
- Optional SuperiorSkyblock2 integration via the published `SuperiorSkyblockAPI` dependency (`provided` scope, version `2026.3`).

### New configuration files

- `missions/daily-missions.yml`: mission goals, reward definitions, reset timezone and reminder interval.
- `boosters/`: automatic schedule, three separate booster offer files, chat rewards and active giveaway state.
- `guis/`: every editable menu layout, including `booster.yml`, `dailymissions.yml` and `sellwandshop.yml`.
- Player snapshots: `player-data/`; daily progress: `daily-missions/`; active giveaway state: `boosters/state.yml`.

## Existing plugin documentation (1.0.6 baseline retained)

Fantasy-themed **drop generator** plugin for **Paper / Spigot 1.21+** (Java 21+).
Inspired by the idea of AxGens, but every line of code, mechanic and API is written from scratch.

## Table of contents
1. [Features](#features) · 2. [Installation](#installation) · 3. [Folder structure](#folder-structure) · 4. [Commands](#commands) · 5. [Permissions](#permissions)
6. [Built-in placeholders](#built-in-placeholders) · 7. [PlaceholderAPI placeholders](#placeholderapi-placeholders) · 8. [Item references](#item-references)
9. [Trading (`/octagens trade`)](#trading-octagens-trade) · 10. [Sellwand](#sellwand) · 11. [MythicLib support](#mythiclib-support) · 12. [Head textures](#head-textures)
13. [Public API](#public-api) · 14. [Build](#build) · 15. [Changelog](#changelog)

## Features
- **20 generators** (Lesser Mana → Genesis Relic). Drop items show their sell price, tier and source generator.
- **Magicule (Mcu)**: every player has an Mcu pool (default max **2000**, starts at **0**). **Magicule Power Collectors** regenerate Mcu per second into the pool (also while the owner is offline). **Every generator drop consumes Mcu** (`energy-requirement`). The maximum can be upgraded with money up to **10,000,000**.
- **MythicLib support**: the player's `MAX_MANA_REGENERATION` stat (percent) boosts the Mcu regeneration.
- **Sellwand**: right-click a **chest** (or other allowed container) to sell the generator drops inside. Hex colours, custom symbol, custom name + model texture, decimal multiplier with no upper limit.
- **Trading** (`/octagens trade`): exchange generator drops (and any other item) for rewards such as **MMOItems**. Unlimited categories, trades, ingredients and requirements.
- **Generator upgrade** from the manage GUI (right-click a generator), **Magicule Shop**, **Generator Shop**, `/octagens sell`, stone/basalt **ore generators** (waterlogged blocks supported), **weekend bonus** (2x–4x, UTC → shown in WIB) and manual booster events with a boss bar.
- **5 languages**: Indonesian, English, Chinese, Japanese, Spanish (`general.language`).
- **Temporary holograms** with unique symbols (info on place/click, `✦ Drop! ✦` on drop, `⚡ +X Mcu/s` on every collector).
- **6-row main menu**, anti-dupe GUI handling, piston/explosion/dispenser/fluid protection, CoreProtect logging, SQLite/MySQL storage (all I/O async).

## Installation
1. Install **Vault** (+ an economy plugin), **PlaceholderAPI** and **CoreProtect** (required). Optional: MMOItems, MythicLib, ItemsAdder, ExcellentEconomy and SuperiorSkyblock2.
2. Put `OctaGens-1.0.6.jar` into `plugins/` and start the server (HikariCP, sqlite-jdbc and mariadb-jdbc are downloaded from Maven Central through `libraries:` in `plugin.yml`, so the first start needs internet access).
3. Set the head textures of the Magicule Power Collectors (see [Head textures](#head-textures)).
4. Edit the config files and run `/octagens reload`.

**Upgrading:** files of the old flat layout (1.0.0–1.0.3) are moved to `plugins/OctaGens/legacy/` automatically. If you copy a new release over a Git checkout, delete files that were removed upstream (for example `hook/AxSellwandsHook.java`, removed in 1.0.4) so the build does not fail on stale sources.

## Folder structure
```
plugins/OctaGens/
├── config.yml          main settings (stays in the root)
├── octagens.db         SQLite database (stays in the root)
├── lang/               id.yml  en.yml  zh.yml  ja.yml  es.yml
├── generators/         generators.yml
├── energy/             energy.yml  energyshop.yml  textures.yml
├── oregen/             oregen.yml
├── sellwand/           sellwand.yml
├── trade/              trade.yml
├── missions/           daily-missions.yml (goals, rewards and WIB reset)
├── boosters/           automatic.yml  gen-drop.yml  sell-multi.yml  mcu-gain.yml  chat-rewards.yml  state.yml
├── player-data/        per-player data snapshots
├── daily-missions/     per-player daily progress
├── guis/               main.yml  shop.yml  energyshop.yml  mpcupgrade.yml  confirm.yml  manage.yml  sell.yml
│                       trade.yml  tradecategory.yml  booster.yml  dailymissions.yml  sellwandshop.yml
├── logs/               sell-log.txt
└── legacy/             old files moved here automatically
```
Texts in `guis/`, `generators/`, `energy/` and `sellwand/` use `{lang:key}` tokens that are replaced with the text of the active language file; you may replace them with plain text.

## Commands
Aliases: `/ogens`, `/og`.

| Command | Description | Permission |
|---|---|---|
| `/octagens` | Main menu (6 rows) | `octagens.use` |
| `/octagens shop` | Generator shop | `octagens.use` |
| `/octagens magiculeshop` | Magicule Shop: buy collectors + upgrade max Mcu | `octagens.use` |
| `/octagens upgrade [amount\|max]` | Upgrade max Mcu with money | `octagens.use` |
| `/octagens sell [hand]` | Sell generator drops from your inventory | `octagens.use` |
| `/octagens trade [category]` | Trade drops for items (MMOItems, ...) | `octagens.trade` |
| `/octagens list` | Your generators / collectors / Mcu | `octagens.use` |
| `/octagens ver` · `/octagens version` | Plugin version, server, language, hooks | `octagens.use` |
| `/octagens give <player> <generator\|energy> <id> [amount]` | Give a generator / collector | `octagens.admin.give` |
| `/octagens sellwand shop` | Open the six-row Sellwand Collection shop | `octagens.use` |
| `/octagens sellwand give <player> <wand> [amount] [uses] [multiplier]` | Give a sellwand (decimal multiplier, no upper limit) | `octagens.admin.give` |
| `/octagens settexture <energy_id> [base64\|url]` | Set a collector head texture (from the head in your hand) | `octagens.admin.give` |
| `/octagens remove <player> <generators\|energy\|all> [id]` | Remove a player's blocks | `octagens.admin.remove` |
| `/octagens setbonus <multiplier\|off> [duration]` | Manual drop booster event | `octagens.admin.bonus` |
| `/octagens reset energy <player>` | Reset current Magicule to configured default | `octagens.admin.energy` |
| `/octagens set energylevel <player> <level>` | Set maximum Magicule upgrade level | `octagens.admin.energy` |
| `/octagens giveaway <drop-booster\|mcu-booster\|sell-booster>` | Open purchase GUI for a server-wide booster giveaway | `octagens.use` |
| `/mpc` | Open the Magicule Power Collector shop | Player |
| `/mpc upgrade` | Open the Max Magicule upgrade GUI | Player |
| `/mpc takeall` | Pick up all your placed collectors | Player |
| `/dailymissions` | Open Daily Missions and claim completed rewards | Player |
| `/octagens reload` | Reload everything (progress is printed to the console) | `octagens.admin.reload` |

Right-click a generator = manage GUI (info, **upgrade generator**, pick up). Quick pickup: **sneak + left-click** a generator or collector.

## Permissions
| Permission | Default | Description |
|---|---|---|
| `octagens.use` | true | Place generators/collectors and use the basic commands |
| `octagens.trade` | true | Use `/octagens trade` |
| `octagens.sellwand.use` | true | Use sellwands |
| `octagens.edit` | op | Edit / pick up other players' generators and collectors |
| `octagens.generatorslot.<n>` / `.*` | false | Generator slot limit (highest wins) / unlimited |
| `octagens.energyslot.<n>` / `.*` | false | Collector slot limit / unlimited |
| `octagens.sellmultiplier.<x>` | false | Sell multiplier, decimals allowed (e.g. `octagens.sellmultiplier.1.5`) |
| `octagens.admin.give` · `.remove` · `.bonus` · `.energy` · `.reload` · `.bypass` | op | Admin commands / bypass slot limits |
| `octagens.admin.*` | op | All admin permissions (includes `octagens.edit`) |

## Built-in placeholders
Built-in placeholders use curly braces (`{name}`) and are replaced by OctaGens itself in the YAML files and language files. Which ones are available depends on where you use them.
`{lang:key}` is replaced with the text of `key` from the active language file. `{prefix}` (messages only) is the chat prefix.

### Generator item, drop item and generator lore (`generators/generators.yml`, `lang/*.yml`)
| Placeholder | Value |
|---|---|
| `{id}` | Generator id |
| `{name}` | Generator display name |
| `{tier}` | Tier number (1–20, by `shop-slot` order) |
| `{drop_name}` | Display name of the drop item |
| `{interval}` | Seconds between drops |
| `{amount}` | Items per drop |
| `{energy}` | Mcu consumed per drop (`energy-requirement`) |
| `{price}` | Shop price (formatted with its currency) |
| `{sell_price}` | Sell price per drop item |

### Magicule Power Collector (`energy/energy.yml`, `energy.item-name-suffix`)
| Placeholder | Value |
|---|---|
| `{name}` | Collector display name |
| `{mcu}` | Regeneration of the collector in Mcu/s |
| `{price}` · `{status}` · `{ingredients}` | Only in Magicule Shop icons: price, purchase status, ingredient list with ✔/✘ |

### Holograms (`lang/*.yml`: `holo-lines`, `holo-drop`, `holo-energy`)
| Placeholder | Value |
|---|---|
| `{name}` `{tier}` `{drop_name}` `{interval}` `{amount}` `{id}` `{price}` `{sell_price}` | Same as the generator placeholders above |
| `{owner}` | Owner name |
| `{pool}` · `{pool_max}` | Owner's current / maximum Mcu |
| `{regen}` | Owner's total regeneration in Mcu/s (including MythicLib bonus) |
| `{required}` | Mcu consumed per drop |
| `{efficiency}` | Readiness: pool ÷ required, as a percentage |
| `{status}` | Status text (`holo-status-*`) |
| `{gain}` · `{mcu}` | Collector pulse only: the **regeneration rate of that collector in Mcu/s** (e.g. `0.5`), including the MythicLib bonus |

### GUI files (`guis/*.yml`)
Available in every GUI (title, names, lore):

| Placeholder | Value |
|---|---|
| `{player}` | Viewer name |
| `{generators}` · `{energies}` | Placed generators / collectors |
| `{generators_max}` · `{energy_max}` | Slot limits (main menu) |
| `{mcu}` · `{mcu_max}` · `{regen}` | Current Mcu, max Mcu, regeneration per second |
| `{level}` · `{step}` · `{limit}` · `{remaining}` | Upgrade level, Mcu per level, absolute max Mcu, levels left |
| `{next_max}` · `{price}` · `{price10}` | Max after +1 upgrade, price of 1 / 10 levels |
| `{page}` · `{pages}` | Pagination (shop-like GUIs) |

Per GUI:

| GUI | Extra placeholders |
|---|---|
| `shop.yml`, `confirm.yml` | All generator placeholders + `{status}` |
| `energyshop.yml` | `{price}` `{status}` `{ingredients}` `{name}` `{mcu}` |
| `manage.yml` | Generator placeholders + `{owner}` `{pool}` `{pool_max}` `{regen}` `{required}` `{efficiency}` `{status}` `{x}` `{y}` `{z}` `{world}` `{multiplier}`; next tier: `{next_name}` `{next_tier}` `{next_drop_name}` `{next_sell_price}` `{next_energy}` `{next_interval}` `{next_amount}`; `{upgrade_price}` |
| `sell.yml` | `{items}` `{total}` `{multiplier}` `{lines}` |
| `trade.yml` | `{count}` `{category}` (category icons) |
| `tradecategory.yml` | `{category}` `{ingredients}` `{requirements}` `{status}` `{amount}` `{name}` `{used}` `{limit}` |
| `booster.yml` | Category, multiplier, duration menu titles, slots, icons, names, lore and frame layout |
| `dailymissions.yml` | Mission menu size, title, decorations, slots, state-specific names/lore and reward display format |
| `sellwandshop.yml` | Six-row shop layout, Sellwand icon/name/lore templates, product slots and close/decorative items |

GUI action strings (`[console]`, `[player]`, `[message]`, `[sound]`) accept `{player}`. Items also accept any PlaceholderAPI placeholder (see below).

### Sellwand (`sellwand/sellwand.yml`)
`{symbol}` custom symbol · `{id}` wand id · `{uses}` remaining uses (`∞` if unlimited) · `{multiplier}` wand multiplier · `{cooldown}` cooldown in seconds · `{player}` holder · `{server_name}` server name.

Player stats: `{mcu}`, `{mcu_max}`, `{mcu_level}`, `{mcu_regen}`, `{generator_count}`, `{mpc_count}`. SuperiorSkyblock2 placeholders: `{island_name}`, `{island_owner}`, `{island_members}`, `{island_role}`. Add arbitrary per-wand replacements under `placeholders:` in each wand definition and use them as `{your_key}`.

### Boss bar / action bar booster (`config.yml` → `weekend-bonus.bossbar.title`, `weekend-bonus.actionbar-text`)
| Placeholder | Value |
|---|---|
| `{booster}` | Booster number without `x`: `3`, `2.5` or a random range such as `2.0-4.0` |
| `{duration}` · `{remaining}` | Remaining time (e.g. `1d 2h 3m`) |
| `{multiplier}` | Booster with `x` (e.g. `2.0x-4.0x`, including a manual event) |

### Weekend / language messages (`lang/*.yml`)
`{multiplier}` `{start}` `{end}` (weekend start text, shown in the display time zone) · `{player}` `{id}` `{value}` `{amount}` `{name}` `{max}` `{limit}` `{levels}` `{price}` `{total}` `{owner}` `{seconds}` `{uses}` `{category}` `{ms}` `{duration}` `{generators}` `{energies}` `{pool}` `{pool_max}` `{mcu}` `{count}` `{version}` `{server}` `{lang}` `{db}` `{hooks}` — the message texts show which ones each message receives.

### Trade (`trade/trade.yml`)
`{player}` in `commands:`. Ingredient/requirement lines in GUIs use the placeholders listed under `tradecategory.yml`.

## PlaceholderAPI placeholders
Identifier: `octagens` (use as `%octagens_<name>%`). All values are formatted: decimals as `1.2`, `120.5`; percentages with `%`.

| Placeholder | Returns |
|---|---|
| `%octagens_magicule_energy_me%` | Total Mcu/s of all your collectors (base value, no bonus) |
| `%octagens_magicule_regen_me%` | Effective Mcu/s including the MythicLib bonus |
| `%octagens_magicule_current_me%` | Your current Mcu (starts at 0) |
| `%octagens_magicule_max_me%` | Your maximum Mcu |
| `%octagens_magicule_percent_me%` | Pool fill percentage |
| `%octagens_magicule_level_me%` | Max-Mcu upgrade level you bought |
| `%octagens_magicule_upgrade_price_me%` | Price of the next upgrade level (or `MAX`) |
| `%octagens_generators_placed%` · `%octagens_generators_max%` | Placed generators / slot limit (`∞` if unlimited) |
| `%octagens_energy_placed%` · `%octagens_energy_max%` | Placed collectors / slot limit |
| `%octagens_efficiency_me%` | Average readiness of your generators (pool ÷ Mcu needed per drop) |
| `%octagens_weekend_active%` | Whether the weekend bonus is active (Yes/No in the active language) |
| `%octagens_weekend_multiplier%` | Current booster text (e.g. `2x-4x`) |
| `%octagens_weekend_remaining%` | Remaining weekend time (displayed time zone) |

**External placeholders from other plugins** (any PlaceholderAPI expansion, such as `%player_name%` or `%vault_eco_balance%`) work inside GUI names and lore and inside trade `requirements.placeholders` conditions, for example `%player_level% >= 10`.

## Item references
Used in every config file (`item`, `block`, `drop-item`, `result`, ingredients...):

| Reference | Meaning |
|---|---|
| `VANILLA:MATERIAL` | Vanilla item (as an ingredient it must be plain: no name, lore or model data) |
| `MMOITEMS:TYPE:ID` | MMOItems item, e.g. `MMOITEMS:MATERIAL:POLISHED_DIAMOND_BLOCK` |
| `ITEMSADDER:namespace:id` | ItemsAdder item |
| `HEAD:<base64>` | Custom player head |
| `DROP:<generator_id>` | Generator drop item (ingredients/results), e.g. `DROP:lesser_mana` |
| `ENERGY:<id>` · `GENERATOR:<id>` | Collector / generator item |

Currency format: `vault` or `excellenteconomy:<currency_id>`. Ingredient format: `<amount>x <reference>`, e.g. `64x DROP:lesser_mana`.

## Trading (`/octagens trade`)
Exchange generator drops for rewards — typically MMOItems. It is **not** a button in the `/octagens` menu; players use `/octagens trade [category]`. Everything is defined in `trade/trade.yml` (reload with `/octagens reload`).

```yaml
categories:
  weapons:                          # unlimited categories
    display-name: "<gradient:#f12711:#f5af19><bold>Weapons</bold></gradient>"
    icon: { item: "VANILLA:DIAMOND_SWORD", glow: true }
    permission: ""                  # optional: permission to open the category
    trades:                         # unlimited trades per category
      mystic_blade:
        result: "MMOITEMS:SWORD:MYSTIC_BLADE"
        amount: 1
        ingredients:
          - "64x DROP:lesser_mana"
          - "32x DROP:arcane_shard"
        requirements:
          permission: ""            # optional
          money: 50000              # optional (+ currency: "vault")
          placeholders:             # optional PlaceholderAPI conditions: >= <= > < == !=
            - condition: "%octagens_generators_placed% >= 3"
              description: "Place at least 3 generators"
        limit: 1                    # per-player trade limit, 0 = unlimited (stored in the database)
        commands: ["[console] say {player} crafted a blade"]
```
Trades are **atomic**: ingredients, money, permission, conditions and limit are all checked first; if anything is missing nothing is taken. A trade using MMOItems/ItemsAdder is shown as *Unavailable* if that plugin or item does not exist.

## Sellwand
OctaGens has its own sellwand (`sellwand/sellwand.yml`). **Right-click a chest** (CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, HOPPER, DROPPER, DISPENSER — configurable) that contains generator drops; the drops are sold at their `sell-price`. Items lying on top of a generator are **not** sold, and non-OctaGens items inside the chest are untouched. Protection plugins are respected and the sale is logged to CoreProtect.

- `symbol` and `{symbol}` for custom symbols, hex/gradient colours, `custom-model-data` / `item-model` / `HEAD:` for custom model textures.
- **multiplier**: any decimal, no upper limit (`/octagens sellwand give Steve master 1 -1 1000.5`). It stacks with `octagens.sellmultiplier.<x>`.
- **uses**: a number, or `-1`/`0` for unlimited.

## MythicLib support
If MythicLib is installed (`mythiclib.enabled`), the player's stat (`mythiclib.stat`, default `MAX_MANA_REGENERATION`) boosts the Mcu regeneration of the player's collectors. The stat is read through `MMOPlayerData#getStatMap()` (reflection, no compile-time dependency) and only for **online** players.

| `mythiclib.mode` | Formula | Example (collectors 16.5 Mcu/s, stat 2) |
|---|---|---|
| `PERCENT` (default) | `regen = base × (1 + stat × scale / 100)` | 16.5 × 1.02 = **16.83 Mcu/s** |
| `FLAT` | `regen = base + stat × scale` | 16.5 + 2 = 18.5 Mcu/s |

`scale` multiplies the stat before it is used (set `0.1` if the stat value 2 should be read as 0.2%).

## Head textures
Collectors use player-head textures. They are empty by default; a collector without a valid texture uses a fallback block (`energy.fallback-block`, default `AMETHYST_BLOCK`) so it is never shown as a broken head. To set a texture: hold a custom head (e.g. a *core* head from minecraft-heads.com) and run `/octagens settexture <energy_id>`, or paste a base64 `Value` / `textures.minecraft.net` URL. Textures are stored in `energy/textures.yml`.

## Public API
```xml
<repository><id>octagens-repo</id><url>https://repo.example.com/releases/</url></repository>
<dependency>
    <groupId>com.octagens</groupId>
    <artifactId>OctaGensAPI</artifactId>
    <version>1.0.6</version>
    <scope>provided</scope>
</dependency>
```
Events: `GeneratorPlace/Break/Pickup/Drop/Purchase/UpgradeEvent`, `EnergyPlace/Break/Purchase/EfficiencyChangeEvent`, `McuMaxUpgradeEvent`, `DropSellEvent`, `WeekendBonusStart/EndEvent`, `OreGeneratorFormEvent`. `OctaGensAPI.get()` exposes generators/collectors, Mcu, slots and weekend status.

## Build
```
mvn clean package
```
Output: `octagens-plugin/target/OctaGens-1.0.6.jar` (the API is shaded in) and `octagens-api/target/OctaGensAPI-1.0.6.jar`. See `BUILDING.md`. A GitHub Actions workflow is in `.github/workflows/build.yml`.

## Changelog
### 1.0.6
- **Player data:** per-player snapshots for Magicule current/max/level/regen, placed Generator/MPC locations, daily mission progress and giveaway claim state. Existing SQLite/MySQL data remains authoritative for pools and placed blocks.
- **Admin commands:** `/octagens reset energy <player>` and `/octagens set energylevel <player> <level>`; `octagens.admin.energy` defaults to OP.
- **Sellwand:** built-in player/server/Magicule and SuperiorSkyblock island placeholders plus custom placeholders per wand, without requiring language keys for default lore.
- **MPC:** `/mpc`, `/mpc upgrade`, `/mpc takeall`; Shift + right-click on an owned MPC opens the Max Magicule upgrade menu.
- **Daily Missions:** `/dailymissions`, WIB midnight reset, persistent progress, configurable reminders, sell/upgrade/purchase/playtime objectives and `VANILLA`, `MMOITEMS`, `COMMAND` rewards.
- **Boosters:** separate Generator Drop, Sell and MCu Gain tracks; configurable weekend/Sunday/daily-random boosts; giveaway purchases stack additively with automatic boosts and reward players for configured chat phrases.
- **SuperiorSkyblock2:** optional integration uses `SuperiorSkyblockAPI` only; island `BUILD` and `BREAK` privileges are checked for OctaGens blocks.

### 1.0.6
- **New:** `/octagens trade` — trade generator drops and other items for rewards such as MMOItems; unlimited categories/trades, ingredients (`DROP:<id>`, MMOItems, ItemsAdder, vanilla...), money, permission, PlaceholderAPI conditions, per-player limits, extra commands; GUIs `trade.yml` / `tradecategory.yml`; permission `octagens.trade`.
- **New:** MythicLib support — the player's `MAX_MANA_REGENERATION` stat boosts Mcu regeneration (`mythiclib` section in `config.yml`); PlaceholderAPI `%octagens_magicule_regen_me%`.
- **Changed:** the sellwand is used by right-clicking a **chest** (allowed containers in `sellwand.yml`) and no longer sells items lying on a generator.
- **Changed:** the `{gain}` placeholder of the collector hologram now shows the collector's regeneration rate in Mcu/s (e.g. `0.5`) instead of the amount gained.
- **Docs:** README is now entirely in English, with tables of built-in and PlaceholderAPI placeholders and a full changelog.

### 1.0.4-BETA
- Own sellwand (hex colours, custom symbol, custom name + model texture, decimal multiplier with no upper limit); the AxSellwands hook was removed.
- Boss bar booster text `Generator Drop Boost: <booster>x | <duration>` (no bold) editable in `config.yml`.
- New folder structure (`lang/`, `generators/`, `energy/`, `oregen/`, `sellwand/`, `guis/`, `logs/`) with automatic migration of old files to `legacy/`; 5 languages (id, en, zh, ja, es).
- `/octagens version`; 6-row main menu with the viewer's own head; safe head textures (`/octagens settexture`, fallback block); holograms with unique symbols.

### 1.0.3-BETA
- Stone generator now also works when lava meets **waterlogged** blocks (slabs, stairs, fences, trapdoors...) and kelp/seagrass.
- AxSellwands hook: configurable wand detection and `/octagens axdebug` diagnostics (hook removed again in 1.0.4).

### 1.0.2-BETA
- Collector items are named **Magicule Power Collector** and show their Mcu/s in the item name.
- Holograms: `Drop!` when a generator drops, and a regeneration pulse above every collector; info hologram on place/click.
- Generator upgrade icon in the manage GUI (price = next tier's shop price, configurable).
- Console startup banner (OG logo), step-by-step reload output, version bump, AxSellwands hook.

### 1.0.1
- **Magicule pool**: players have an Mcu pool (starts at 0, default max 2000); collectors regenerate it (also offline); every generator drop consumes Mcu; generators wait with "low energy" status.
- Upgradeable maximum Mcu with money (limit 10,000,000) via the Magicule Shop or `/octagens upgrade`.
- Pickup changed to **sneak + left-click**; temporary holograms; drop items show sell price and source; `/octagens energyshop` renamed to `/octagens magiculeshop`; first built-in sellwand; Mcu placeholders and API methods.

### 1.0.0
- Initial release: 20 generators with the specified shop and sell prices, 10 energy types (Mcu/s), sell command, generator shop and energy shop (atomic purchases), weekend bonus (UTC → WIB display), stone/basalt ore generators, slot limits via permissions, YAML-driven GUIs, commands with tab completion, PlaceholderAPI expansion, CoreProtect logging and anti-exploit protections, public API (`octagens-api`) and SQLite/MySQL storage.


## Release notes: 1.0.6

See [CHANGELOG.md](CHANGELOG.md) for the complete update notes.
