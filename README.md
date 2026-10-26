# OctaGens 1.0.4-BETA

Plugin generator drop bertema fantasy untuk **Paper/Spigot 1.21+** (Java 21+).
Terinspirasi konsep AxGens, tetapi seluruh kode, mekanik, dan API ditulis dari nol.

## Fitur
- **20 generator drop** (Lesser Mana → Genesis Relic). Item drop menampilkan harga jual, tier, dan generator asal.
- **Magicule (Mcu)**: tiap pemain punya pool Mcu (default maks 2000, awalnya **0**). **Magicule Power Collector** meregen Mcu/s ke pool (juga saat offline); **setiap drop generator mengonsumsi Mcu** sebesar `energy-requirement`. Mcu maksimum bisa di-upgrade dengan uang sampai 10.000.000.
- **Sellwand sendiri**: klik kanan **generator** untuk menjual drop di sekitarnya. Hex color, simbol kustom (`{symbol}`), nama + model texture (custom-model-data / item-model / head), **multiplier tanpa batas dan boleh desimal**, pemakaian terbatas/tak terbatas.
- **Upgrade generator** di GUI manage (klik kanan generator), **Magicule Shop**, **Generator Shop**, sell (`/octagens sell`), ore generator (stone/basalt, termasuk block waterlogged), weekend bonus 2x–4x (UTC → tampil WIB) dan event booster manual.
- **Bossbar booster**: `Generator Drop Boost: <booster>x | <durasi>` (tanpa bold, bisa diedit di `config.yml`).
- **5 bahasa**: Indonesia, English, 中文, 日本語, Español (`general.language`).
- **Hologram sementara** dengan simbol unik: info saat dipasang/diklik, `✦ Drop! ✦` saat drop, `⚡ +X Mcu` di tiap collector.
- **GUI** 6 baris di `/octagens` dengan kepala pemain sendiri (selalu punya skin valid).
- Keamanan: anti dupe GUI, rate limit, proteksi piston/ledakan/dispenser/fluid, CoreProtect, storage SQLite/MySQL async.

## Struktur folder `plugins/OctaGens/`
```
config.yml                 <- pengaturan utama (di luar)
octagens.db                <- database SQLite (di luar)
lang/       id.yml en.yml zh.yml ja.yml es.yml
generators/ generators.yml
energy/     energy.yml  energyshop.yml  textures.yml
oregen/     oregen.yml
sellwand/   sellwand.yml
guis/       main.yml shop.yml energyshop.yml confirm.yml manage.yml sell.yml
logs/       sell-log.txt
legacy/     (file lama 1.0.0-1.0.3 dipindah otomatis ke sini)
```
Teks di `guis/`, `generators/`, `energy/`, `sellwand/` memakai token `{lang:kunci}` dari folder `lang/`; boleh diganti teks biasa.

## Instalasi
1. Pasang **Vault** (+ economy), **PlaceholderAPI**, **CoreProtect** (wajib). Opsional: MMOItems, ItemsAdder, ExcellentEconomy.
2. Taruh `OctaGens-1.0.4-BETA.jar` di `plugins/`, start server (library Hikari/sqlite/mariadb diunduh otomatis dari Maven Central).
3. **Head texture Magicule Power Collector**: ambil head bertema *core* dari minecraft-heads.com, pegang di tangan, lalu `/octagens settexture <id>` (tersimpan di `energy/textures.yml`). Collector tanpa texture valid memakai block cadangan (`energy.fallback-block`) sehingga tidak pernah tampil sebagai head rusak.
4. Upgrade dari 1.0.0–1.0.3: file lama dipindah otomatis ke `legacy/`; salin pengaturan kustom Anda ke file baru bila perlu.

## Command
| Command | Fungsi | Permission |
|---|---|---|
| `/octagens` (alias `/ogens`, `/og`) | Menu utama (6 baris) | `octagens.use` |
| `/octagens shop` · `magiculeshop` · `upgrade [jumlah\|max]` · `sell [hand]` · `list` | Toko, upgrade Mcu, jual, daftar | `octagens.use` |
| `/octagens ver` / `version` | Versi plugin, server, bahasa, hook | `octagens.use` |
| `/octagens give <player> <generator\|energy> <id> [jumlah]` | Beri item | `octagens.admin.give` |
| `/octagens sellwand give <player> <wand> [jumlah] [uses] [multiplier]` | Beri sellwand (multiplier desimal, tanpa batas) | `octagens.admin.give` |
| `/octagens settexture <energy_id> [base64\|url]` | Atur head texture collector (dari head di tangan) | `octagens.admin.give` |
| `/octagens remove <player> <generators\|energy\|all> [id]` | Hapus milik pemain | `octagens.admin.remove` |
| `/octagens setbonus <multiplier\|off> [durasi]` | Event booster manual | `octagens.admin.bonus` |
| `/octagens reload` | Reload (progres tampil di console) | `octagens.admin.reload` |

Klik kanan generator = GUI manage (info, upgrade generator, ambil). Pickup cepat: **jongkok + klik kiri**.

## Permission
`octagens.use` (true), `octagens.edit` (op), `octagens.sellwand.use` (true), `octagens.generatorslot.<n|*>`, `octagens.energyslot.<n|*>`, `octagens.sellmultiplier.<x>`, `octagens.admin.*` (op: give, remove, bonus, reload, bypass, edit).

## PlaceholderAPI (`%octagens_...%`)
`magicule_energy_me` (total regen Mcu/s), `magicule_current_me`, `magicule_max_me`, `magicule_percent_me`, `magicule_level_me`, `magicule_upgrade_price_me`, `generators_placed`, `generators_max`, `energy_placed`, `energy_max`, `efficiency_me`, `weekend_active`, `weekend_multiplier`, `weekend_remaining`.

## API (module `octagens-api`)
```xml
<repository><id>octagens-repo</id><url>https://repo.example.com/releases/</url></repository>
<dependency>
    <groupId>com.octagens</groupId>
    <artifactId>OctaGensAPI</artifactId>
    <version>1.0.4-BETA</version>
    <scope>provided</scope>
</dependency>
```
Event: `GeneratorPlace/Break/Pickup/Drop/Purchase/UpgradeEvent`, `EnergyPlace/Break/Purchase/EfficiencyChangeEvent`, `McuMaxUpgradeEvent`, `DropSellEvent`, `WeekendBonusStart/EndEvent`, `OreGeneratorFormEvent`. `OctaGensAPI.get()` untuk generator/collector, Mcu, slot, dan status weekend.

## Changelog
**1.0.4-BETA** — Sellwand sendiri (generator only, multiplier desimal tanpa batas), hook AxSellwands dihapus; bossbar booster dari config; struktur folder baru + 5 bahasa + migrasi otomatis; `/octagens version`; menu utama 6 baris; head texture aman (`settexture`, block cadangan); hologram dengan simbol unik.
**1.0.3-BETA** — Stone generator di block waterlogged. **1.0.2-BETA** — hologram sementara, upgrade generator, banner, reload berprogres.

## Build
```
mvn clean package
```
Hasil: `octagens-plugin/target/OctaGens-1.0.4-BETA.jar` (API ikut di-shade). Workflow GitHub Actions ada di `.github/workflows/build.yml`.
