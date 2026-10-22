# OctaGens

Plugin generator drop bertema fantasy untuk **Paper/Spigot 1.21+** (Java 21+).
Terinspirasi konsep AxGens, tetapi seluruh kode, mekanik, dan API ditulis dari nol
(tidak bergantung pada AxGens maupun AxGensAPI).

## Fitur
- **20 generator drop** (Lesser Mana → Genesis Relic) dengan harga shop & harga jual sesuai tabel; semua bisa diedit di `generators.yml`.
- **Energy (Magicule, Mcu/s)**: 10 tier player head custom, radius default 64 block. `efficiency = min(1, supply / energy-requirement)`.
- **Sell**: `/octagens sell`, `/octagens sell hand`, opsi GUI konfirmasi, multiplier via permission, log ke console + `sell-log.txt`.
- **Generator Shop & Energy Shop** berpaginasi, pembelian energy **atomik** (cek semua bahan + uang dulu, baru ambil).
- **Weekend Bonus** drop 2x–4x. Acuan waktu **UTC**, tampilan **WIB (Asia/Jakarta)**. Mode `RANDOM_PER_DROP`, `FIXED_PER_WEEKEND`, `FIXED_PER_DAY`, boss bar/action bar, broadcast, event bonus manual.
- **Stone Generator & Basalt Generator** (ore generator) via `BlockFromToEvent`, chance ternormalisasi, syarat world/Y/biome, varian deepslate, bonus lewat permission.
- **Slot limit** lewat permission + slot bonus (API) tersimpan di database.
- **GUI custom** di `guis/*.yml`: hex/gradient, custom-model-data, item MMOItems/ItemsAdder, head base64, paginasi otomatis, aksi klik.
- **Keamanan**: anti dupe GUI, rate limit klik & cooldown sell, proteksi piston/ledakan/dispenser/fluid, logging CoreProtect.
- **Hologram** TextDisplay per generator (nama, pemilik, Mcu, efficiency, status).
- **Storage** SQLite (default) / MySQL-MariaDB (HikariCP), semua I/O async.

## Instalasi
1. Pasang **Vault** (+ plugin economy), **PlaceholderAPI**, dan **CoreProtect** (wajib).
   Bila salah satu tidak ada, OctaGens mematikan dirinya dan menulis pesan jelas di console.
2. Opsional: MMOItems, ItemsAdder, ExcellentEconomy.
3. Taruh `OctaGens-1.0.0.jar` di folder `plugins/`, start server.
   Pada start pertama server mengunduh library (HikariCP, sqlite-jdbc, mariadb-jdbc, slf4j) dari Maven Central lewat `libraries:` di `plugin.yml`, jadi server perlu akses internet saat itu.
4. **Ganti `head-texture` di `energy.yml`**: nilai bawaan adalah placeholder (skin tidak nyata). Ambil texture base64 dari mis. minecraft-heads.com.
5. Edit config sesuai kebutuhan lalu `/octagens reload`.

## Struktur config (`plugins/OctaGens/`)
```
config.yml  generators.yml  energy.yml  energyshop.yml  oregen.yml  messages.yml
guis/ main.yml shop.yml energyshop.yml confirm.yml manage.yml sell.yml
```
Format item: `VANILLA:MATERIAL`, `MMOITEMS:TYPE:ID`, `ITEMSADDER:namespace:id`, `HEAD:<base64>`.
Format currency: `vault` atau `excellenteconomy:<currency_id>`.

## Command
| Command | Fungsi | Permission |
|---|---|---|
| `/octagens` (alias `/ogens`, `/og`) | Menu utama / help | `octagens.use` |
| `/octagens shop` | Generator shop | `octagens.use` |
| `/octagens energyshop` | Energy shop | `octagens.use` |
| `/octagens sell [hand]` | Jual drop | `octagens.use` |
| `/octagens list` | Generator & energy milikku | `octagens.use` |
| `/octagens give <player> <generator\|energy> <id> [jumlah]` | Beri item | `octagens.admin.give` |
| `/octagens remove <player> <generators\|energy\|all> [id]` | Hapus milik pemain | `octagens.admin.remove` |
| `/octagens setbonus <multiplier\|off> [durasi]` | Event bonus manual | `octagens.admin.bonus` |
| `/octagens reload` | Reload semua config | `octagens.admin.reload` |

Pickup generator/energy: **sneak + klik kanan** (tangan kosong) atau tombol di GUI manage (klik kanan generator).

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
`magicule_energy_me`, `generators_placed`, `generators_max`, `energy_placed`, `energy_max`,
`efficiency_me` (rata-rata efficiency semua generator pemain), `weekend_active`,
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
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```
Tambahkan `depend: [OctaGens]` (atau `softdepend`) di `plugin.yml` plugin kamu.
```java
OctaGensAPI api = OctaGensAPI.get();
double eff = api.getAverageEfficiency(player.getUniqueId());
api.addGeneratorSlots(player.getUniqueId(), 5);

@EventHandler
public void onDrop(GeneratorDropEvent e) { e.setMultiplier(e.getMultiplier() * 2); }
```
Event: `GeneratorPlace/Break/Pickup/Drop/PurchaseEvent`, `EnergyPlace/Break/Purchase/EfficiencyChangeEvent`,
`DropSellEvent`, `WeekendBonusStart/EndEvent`, `OreGeneratorFormEvent`.

## Build
```
mvn clean package
```
Hasil: `octagens-plugin/target/OctaGens-1.0.0.jar` (API ikut di-shade ke dalam jar plugin).
Untuk publish API saja: `mvn -pl octagens-api deploy`.
