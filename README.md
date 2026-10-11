# OctaMMO 1.0.3

Add-on MMOItems untuk **Paper 1.21.11** (Java 21, hanya pemain Java): stasiun upgrade **Altar Forge** (GUI 6 baris) dengan title loading 1% sampai 100%, plus API publik. Modul Item Update sudah dihapus.

## Struktur proyek

```
octammo-parent (pom)
 ├─ octammo-api      interface + event saja (compileOnly untuk developer lain)
 └─ octammo-plugin   implementasi, jar plugin sudah menyertakan class API
```

## Instalasi

1. Pasang **MMOItems** dan **MythicLib**. Tanpa keduanya plugin tetap load, modul upgrade nonaktif dan console menampilkan satu peringatan.
2. Build dengan `mvn clean package` (atau GitHub Actions, lihat `.github/workflows/build.yml`). Hasil: `octammo-plugin/target/OctaMMO-1.0.3.jar`.
3. Taruh jar di `plugins/`, restart. Soft-depend opsional: Vault, PlaceholderAPI, Floodgate, WorldGuard, Excellent Economy (lewat Vault).

Setelah update plugin, key config baru ditambahkan otomatis tanpa menyentuh nilaimu; file lama disalin ke `backups/`. File di `guis/`, `upgrades/`, serta key yang **diganti nama** di `lang/` tidak ikut diperbarui otomatis. Hapus `guis/default-upgrade.yml` dan folder `lang/` (setelah dibackup) supaya dibuat ulang dengan tombol dan teks terbaru.

## Command dan permission

| Command | Permission | Default |
|---|---|---|
| `/octammo help` | `octammo.use` | semua |
| `/octammo version` atau `/octammo ver` | tanpa permission | semua |
| `/octammo upgrade [station] [player] [path]`, `/itemupgrade [station] [path]`, `/octammo:itemupgrade` | `octammo.upgrade` | semua |
| `/octammo reload`, `/octammo list`, buka untuk pemain lain | `octammo.admin` | op |
| izin per stasiun | isi `permission:` di file stasiun | tidak ada |

Alias: `/octa`, `/omm`. `/itemupgrade` tanpa argumen membuka daftar stasiun yang boleh diakses pemain.

`/octammo ver` menampilkan pembuat (dari `plugin.yml`: Alvran, Robozat and Team), versi plugin, dan deskripsi (dari `lang/*.yml`, key `plugin-description`).

## Altar Forge

Satu file di `upgrades/` = satu **stasiun**; tiap entri di `paths:` = satu **path**. Lihat `upgrades/example-sword.yml` (lengkap, ber-komentar). Stasiun dengan banyak path menampilkan menu path (`guis/path-select.yml`).

```
      0     1    2    3    4     5   6    7   8
r0    BACK  ▒    ▒    ▒    INFO  ▒   ▒    ▒   X
r1    ▒     ▒    ▒    C1   C2    C3  ▒    ▒   SYR
r2    ▒     ▒    FILL C4   IN    C5  AMT  OUT ▒
r3    ▒     ▒    ▒    C6   C7    C8  ▒    %   ▒
r4    ▒     BST1 BST2 BST3 GO    ▒   PRT  ▒   ▒
r5    G1    G2   G3   G4   G5    G6  G7   G8  G9
```

- **IN** hanya menerima TYPE/ID path itu. **C1-C8** aktif sebanyak slot yang dibutuhkan cost berbentuk item (satu cost besar dipecah ke beberapa slot sesuai ukuran stack). Slot kosong menampilkan placeholder (bertanda PDC `octammo:virtual`, tidak bisa diambil atau dishift-click).
- **FILL** (tombol isi otomatis): memindahkan item yang diupgrade dan semua material yang dibutuhkan dari inventory ke slot, persis sebanyak yang diperlukan. Booster dan proteksi tidak diisi otomatis. Kalau bahan kurang, pesan menyebut apa yang kurang.
- **AMT** (tombol jumlah result): klik kiri menambah satu langkah, klik kanan mengurangi, shift-klik kembali ke default. Langkahnya adalah jumlah default di file: `amount: 1` menjadi 1, 1+1, 1+1+1; `amount: 2` menjadi 2, 2+2, 2+2+2.
  - Secara default input, cost item, uang, dan level ikut dikalikan, dan **satu roll** menentukan seluruh batch. `output.scale-costs: false` hanya mengalikan result (pengali gratis, hati-hati).
  - Batas tertinggi: `upgrade.max-multiplier` di config (atau `output.max-multiplier` per path), ukuran stack item input, dan jumlah slot rune. Path dengan cost `CUSTOM` tidak bisa dikalikan.
  - Item di slot rune yang tidak cocok lagi setelah jumlah diganti dikembalikan ke pemain.
- **SYR** = checklist requirement dan cost dengan tanda hijau/merah. **%** = chance akhir. **OUT** = preview hasil (jumlahnya ikut berubah). **GO** merah sampai semua syarat terpenuhi.
- **G1-G9** = gauge chance statis: merah di bawah 30%, kuning 30-70%, hijau di atas 70%.
- **BST** = booster, **PRT** = proteksi (opsional, didefinisikan di path).
- Tombol FILL dan AMT opsional di layout (`slots.autofill`, `slots.amount`); hapus dari `guis/*.yml` untuk menyembunyikannya.
- Layout, tema, material, `item-model`, `custom-model-data` ada di `guis/*.yml`; file rusak dilaporkan (file, key, nilai) dan diganti layout bawaan.
- Gaya teks GUI: hanya judul ikon yang bold, lore memakai warna dan simbol saja.

### Proses upgrade

1. GO ditekan: sesi dikunci, semua syarat dicek ulang, uang/level dikonsumsi, **chance di-roll sekali**.
2. GUI dikosongkan dan **ditutup**. Hasil dan semua item yang kembali disimpan di sesi dan di `pending-rewards/` (ditulis async).
3. Title `Upgrade Sedang Berlangsung!!` dan subtitle `1%` sampai `100%`. **Satu task global** untuk semua pemain, hanya hidup selama ada sesi, title dikirim hanya saat persen berubah (`percent-step` mengurangi paket).
4. Pemain dikunci: slowness dan jump negatif (tanpa listener gerak), invulnerable, command/interact/inventory/drop/combat dibatalkan (`upgrade-loading.lock.*`).
5. Di 100%: item diberikan, efek `on-success` / `on-fail-effects` jalan, `logs/upgrades.log` ditulis, event ditembakkan, title hasil tampil, GUI dibuka lagi dalam keadaan kosong.

Pemain disconnect, server stop, atau plugin disable saat loading: hasil diberikan seketika. Bila pemain tidak online, file `pending-rewards` dikirim saat join berikutnya.

### Format path

| Bagian | Isi |
|---|---|
| `requirements` | `level >= 30`, `permission node`, `placeholder %x% >= 30` (PlaceholderAPI), atau requirement dari plugin lain |
| `input` | `type`, `id`, `amount`, `consume` |
| `costs` (`kind`) | `MMOITEMS` dan `VANILLA` (masuk slot rune), `MONEY` (Vault, Excellent Economy lewat Vault), `LEVELS`, `CUSTOM` / `COMMAND_CHECK` (lewat API) |
| `output` | `mode: SINGLE` atau `WEIGHTED`; `results` (`type`, `id`, `amount`, `weight`, `preserve`); `scale-costs`; `max-multiplier` |
| `chance` | `base`, `max-total`, `booster-slots` (0-3), `booster-consume`, `boosters` |
| `on-fail` | `input`: `KEEP`/`CONSUME`/`DOWNGRADE`/`BREAK`; `costs`: `CONSUME`/`REFUND`/`PARTIAL`; `refund-percent`; `protection`; `downgrade` |
| `on-success`, `on-fail-effects` | `commands` (`console:` atau `player:`), `sound`, `title`, `broadcast`, `effects` (API) |
| stasiun | `display-name`, `permission`, `gui`, `cooldown`, `worlds-blacklist`, `worldguard-regions-blacklist`, `loading.duration-ticks` |

Booster bertumpuk: `chance = min(max-total, base + jumlah(add × jumlah item))`. Contoh base 75 dengan +10 per item: 85%, 95%, 100%. Hanya item yang dihitung (maksimal `max-items`) yang dikonsumsi.

`preserve` di result menyalin NBT mentah dari item input (best effort, lore dan stat tidak dihitung ulang). Kosongkan bila ragu.

## Placeholder (PlaceholderAPI)

`%octammo_upgrades_done%`, `%octammo_upgrades_success%`, `%octammo_upgrades_failed%`, `%octammo_can_upgrade_<id>%`.

## API (versi 2)

Gunakan sebagai `compileOnly` (`mvn install` di `octammo-api`) dan `softdepend: [OctaMMO]`.

```java
OctaMMOApi api = OctaMMO.getApi();   // io.octammo.api.OctaMMO, atau Bukkit.getServicesManager().load(OctaMMOApi.class)
api.openStation(player, "example-sword");
api.attemptUpgrade(player, new UpgradeContext("example-sword", "tier-2"));   // tanpa GUI, item diambil dari inventory
api.isUpgrading(player);
```

Event: `UpgradeStationOpenEvent` (cancel), `UpgradePreEvent` (cancel, `setChance`, `getBoosterBonus`), `UpgradeLoadingStartEvent` (cancel, ubah `durationTicks`), `UpgradeLoadingEndEvent`, `UpgradeResultEvent`, `UpgradeCloseEvent`. Method dan event Item Update dari API versi 1 sudah dihapus.

```java
api.registerCostType(new CostType() {
    public String id() { return "my-points"; }
    public boolean has(Player p, ConfigurationSection cfg) { return MyPoints.get(p) >= cfg.getInt("amount"); }
    public void take(Player p, ConfigurationSection cfg) { MyPoints.take(p, cfg.getInt("amount")); }
});
```
YAML: `- { kind: CUSTOM, id: my-points, amount: 100 }`.

## Keterbatasan

- Excellent Economy dipakai lewat Vault; mata uang ganda native belum ada.
- `min-upgrade-level` di `input` belum didukung.
- Struktur package belum dipecah sesuai usulan (`core`, `upgrade`, `gui`, `hook`, `command`, `util`).
