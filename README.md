# OctaGens

Plugin generator drop bertema fantasy untuk **Paper/Spigot 1.21+** (Java 21+).
Terinspirasi konsep AxGens, tetapi seluruh kode, mekanik, dan API ditulis dari nol
(tidak bergantung pada AxGens maupun AxGensAPI).

## Fitur
- **20 generator drop** (Lesser Mana → Genesis Relic) dengan harga shop & harga jual sesuai tabel; semua bisa diedit di `generators.yml`. Item drop menampilkan **harga jual, tier, dan generator asal** di lore.
- **Sistem energy (Magicule / Mcu)**:
  - Tiap pemain punya satu **pool Mcu** (default maksimum **2000**, bisa diubah di `config.yml`).
  - Energy yang dipasang **meregen Mcu/s** ke pool sampai penuh (juga saat offline, disimpan di database).
  - Setiap **drop generator mengonsumsi Mcu** sebesar `energy-requirement` (Mcu per drop). Sebelum pool cukup, generator berstatus **"Energy kurang"** dan menunggu.
  - Generator juga butuh minimal satu energy milik pemilik dalam radius `energy.max-distance` (default 64).
  - **Upgrade Mcu maksimum** dengan uang (Magicule Shop atau `/octagens upgrade`), batas tertinggi **10.000.000 Mcu**, semua angka di config.
- **Sell**: `/octagens sell`, `/octagens sell hand`, opsi GUI konfirmasi, multiplier via permission, log console + `sell-log.txt`.
- **Hook AxSellwands**: OctaGens tidak punya sellwand sendiri. Bila plugin **AxSellwands** terpasang, klik kanan container dengan wand AxSellwands akan menjual drop OctaGens di dalamnya dengan harga OctaGens (sisanya diproses AxSellwands). Lihat `hooks.axsellwands` di `config.yml`.
- **Upgrade generator**: klik kanan generator membuka GUI manage dengan ikon upgrade ke tier berikutnya (harga = harga shop tier berikutnya).
- **Magicule Power Collector**: nama item memuat regen Mcu/s, hologram `+X Mcu` muncul sejenak tiap beberapa detik selagi meregen.
- **Generator Shop & Magicule Shop** berpaginasi, pembelian energy **atomik**.
- **Weekend Bonus** drop 2x–4x (acuan UTC, tampil WIB), boss bar/action bar, broadcast, event bonus manual.
- **Stone Generator & Basalt Generator**, **slot limit** via permission, **GUI custom** YAML, **keamanan** (anti dupe GUI, rate limit, proteksi piston/ledakan/dispenser/fluid, CoreProtect).
- **Hologram** TextDisplay yang hanya muncul **sejenak** lalu dihapus supaya tidak lag: info saat dipasang/diklik, teks **"Drop!"** saat generator drop, dan `+X Mcu` di tiap Magicule Power Collector.
- **Storage** SQLite (default) / MySQL-MariaDB (HikariCP), semua I/O async.

## Changelog
**1.0.3-BETA**
- Stone Generator kini juga aktif saat lava bertemu block *waterlogged* (slab, stairs, pagar, trapdoor, dll) serta kelp/seagrass, sama seperti vanilla.
- Hook AxSellwands: deteksi wand bisa diatur (`wand-pdc-namespaces`, `wand-text-contains`) dan ada `/octagens axdebug` untuk diagnosis (key PDC item, isi container, drop bertag pemilik).
- Catatan: integrasi resmi lewat AxIntegrations (`ShopIntegration`) belum ada; hook saat ini bekerja lewat event interact.

## Instalasi
1. Pasang **Vault** (+ plugin economy), **PlaceholderAPI**, dan **CoreProtect** (wajib).
   Bila salah satu tidak ada, OctaGens mematikan dirinya dan menulis pesan jelas di console.
2. Opsional: MMOItems, ItemsAdder, ExcellentEconomy, **AxSellwands** (untuk sellwand).
3. Taruh `OctaGens-1.0.3-BETA.jar` di folder `plugins/`, start server.
   Pada start pertama server mengunduh library (HikariCP, sqlite-jdbc, mariadb-jdbc, slf4j) dari Maven Central lewat `libraries:` di `plugin.yml`, jadi server perlu akses internet saat itu.
4. **Ganti `head-texture` di `energy.yml`**: nilai bawaan adalah placeholder (skin tidak nyata). Ambil texture base64 (field "Value") dari minecraft-heads.com, cari head bertema *core* untuk Magicule Power Collector.
5. Edit config sesuai kebutuhan lalu `/octagens reload`.

## Struktur config (`plugins/OctaGens/`)
```
config.yml  generators.yml  energy.yml  energyshop.yml  oregen.yml  messages.yml
guis/ main.yml shop.yml energyshop.yml (= Magicule Shop) confirm.yml manage.yml sell.yml
```
Format item: `VANILLA:MATERIAL`, `MMOITEMS:TYPE:ID`, `ITEMSADDER:namespace:id`, `HEAD:<base64>`.
Format currency: `vault` atau `excellenteconomy:<currency_id>`.

## Command
| Command | Fungsi | Permission |
|---|---|---|
| `/octagens` (alias `/ogens`, `/og`) | Menu utama / help | `octagens.use` |
| `/octagens shop` | Generator shop | `octagens.use` |
| `/octagens magiculeshop` | Magicule shop (beli energy + upgrade Mcu maks) | `octagens.use` |
| `/octagens upgrade [jumlah\|max]` | Upgrade Mcu maksimum dengan uang | `octagens.use` |
| `/octagens sell [hand]` | Jual drop | `octagens.use` |
| `/octagens list` | Generator & energy milikku | `octagens.use` |
| `/octagens give <player> <generator\|energy> <id> [jumlah]` | Beri item | `octagens.admin.give` |
| `/octagens remove <player> <generators\|energy\|all> [id]` | Hapus milik pemain | `octagens.admin.remove` |
| `/octagens setbonus <multiplier\|off> [durasi]` | Event bonus manual | `octagens.admin.bonus` |
| `/octagens axdebug` | Diagnosis hook AxSellwands (pegang wand, lihat chest) | `octagens.admin.reload` |
| `/octagens reload` | Reload semua config (progres tampil di console) | `octagens.admin.reload` |

Klik kanan generator = GUI manage (info, **upgrade generator**, ambil generator). Pickup cepat: **jongkok (sneak) + klik kiri** pada generator/collector.

## Permission
| Permission | Default | Fungsi |
|---|---|---|
| `octagens.use` | true | Pakai/pasang generator, command dasar |
| `octagens.edit` | op | Edit/pickup generator & energy milik orang lain |
| `octagens.generatorslot.<n>` / `.*` | false | Batas slot generator (tertinggi dipakai) / tak terbatas |
| `octagens.energyslot.<n>` / `.*` | false | Batas slot energy / tak terbatas |
| `octagens.sellmultiplier.<x>` | false | Multiplier jual (mis. `1.5`) |
| `octagens.oregen.bonus.*` | false | Contoh permission bonus ore generator (diatur di `oregen.yml`) |
| `octagens.admin.*` | op | Semua admin (edit, give, remove, bonus, reload, bypass slot) |

## PlaceholderAPI (`%octagens_...%`)
`magicule_energy_me` (total regen Mcu/s), `magicule_current_me`, `magicule_max_me`, `magicule_percent_me`, `magicule_level_me`, `magicule_upgrade_price_me`, `generators_placed`, `generators_max`, `energy_placed`, `energy_max`,
`efficiency_me` (rata-rata kesiapan = Mcu pool / konsumsi per drop), `weekend_active`,
`weekend_multiplier`, `weekend_remaining` (waktu tampil dalam WIB).

## API (module `octagens-api`)
```xml
<repository>
    <id>octagens-repo</id>
    <url>https://repo.example.com/releases/</url>
</repository>

<dependency>
    <groupId>com.octagens</groupId>
    <artifactId>OctaGensAPI</artifactId>
    <version>1.0.3-BETA</version>
    <scope>provided</scope>
</dependency>
```
Tambahkan `depend: [OctaGens]` (atau `softdepend`) di `plugin.yml` plugin kamu.
```java
OctaGensAPI api = OctaGensAPI.get();
double eff = api.getAverageEfficiency(player.getUniqueId());
api.addGeneratorSlots(player.getUniqueId(), 5);
double mcu = api.getMcuCurrent(player.getUniqueId());

@EventHandler
public void onDrop(GeneratorDropEvent e) { e.setMultiplier(e.getMultiplier() * 2); }
```
Event: `GeneratorPlace/Break/Pickup/Drop/Purchase/UpgradeEvent`, `EnergyPlace/Break/Purchase/EfficiencyChangeEvent` (hanya saat generator berganti antara cukup/kurang Mcu), `McuMaxUpgradeEvent`,
`DropSellEvent`, `WeekendBonusStart/EndEvent`, `OreGeneratorFormEvent`.

## Build
```
mvn clean package
```
Hasil: `octagens-plugin/target/OctaGens-1.0.3-BETA.jar` (API ikut di-shade ke dalam jar plugin).
Untuk publish API saja: `mvn -pl octagens-api deploy`.
