# OctaMMO

Add-on MMOItems untuk Paper 1.21.11 (Java 21). Dua modul:

- **Item Update**: item MMOItems yang sudah kadaluarsa (habis nerf/buff) diganti otomatis, gratis, tanpa revision ID.
- **Item Upgrade**: stasiun upgrade "Altar Forge" (GUI 6 baris) dengan animasi loading title/subtitle 1% sampai 100%.

## Build

```
mvn clean package
```
Hasil: `target/OctaMMO-1.0.0.jar`. Taruh di `plugins/` bersama MMOItems + MythicLib. Atau pakai GitHub Actions (`.github/workflows/build.yml`).
Versi dependency di `pom.xml` (MMOItems-API, MythicLib-dist, WorldGuard, Floodgate) perlu dicek ke repo masing-masing kalau resolve gagal.

## Item Update (`updates.yml`)

Tiap entri di `items:` menjelaskan cara mengenali salinan lama satu item (TYPE + ID + lore, tanpa warna):

```yaml
items:
  corrupted-sword:
    type: SWORD
    id: CORRUPTED_SWORD
    outdated-lore-contains: ["Attack Damage: 10"]   # ada baris ini -> kadaluarsa
    required-lore-contains: []                      # salah satu tidak ada -> kadaluarsa
    compare-template: false                         # lore beda dari item baru -> kadaluarsa
```

- Penggantian: console menjalankan `mmoitems give <type> <id> <player> <jumlah sebelumnya>` (`method: COMMAND`), atau `method: API` untuk menaruh item baru di slot yang sama.
- Scan saat: join, tutup inventory, pickup item, interval (opsional), `/octammo update <player|all>`.
- Aturan yang juga menandai item BARU sebagai kadaluarsa tidak dipakai (dan di-log), supaya item tidak diganti terus-menerus.
- Setelah mengubah item di MMOItems, jalankan `/mi reload` lalu `/octammo reload`.

## Item Upgrade: Altar Forge

Satu file di `upgrades/` = satu **stasiun**; tiap entri di `upgrades:` = satu **path**.
Buka dengan `/itemupgrade <file> [path]` atau `/octammo:itemupgrade <file>`. Stasiun dengan beberapa path menampilkan menu path dulu (`guis/path-select.yml`).

**Item input dan semua item cost ditaruh pemain langsung di GUI**, baru tombol upgrade ditekan:

```
      0   1   2   3   4   5   6   7   8
r0    .   .   .   .  INFO  .   .   .   .
r1    .   .   .  C1  C2  C3   .   .  SYR
r2    .   .   .  C4  IN  C5   >  OUT  .
r3    .   .   .  C6  C7  C8   .   %   .
r4   BACK  .  BST  .  GO   .  PRT  .   X
r5   P1  P2  P3  P4  P5  P6  P7  P8  P9
```

- **IN** hanya menerima TYPE/ID item path itu. **C1-C8** (cincin rune) aktif sebanyak slot yang dibutuhkan cost berbentuk item; satu cost besar dipecah ke beberapa slot sesuai ukuran stack. Slot sisa terkunci.
- Slot kosong menampilkan placeholder (nama bahan + jumlah). Placeholder bertanda PDC `octammo:virtual`, tidak bisa diambil, dishift-click, atau diberikan.
- **SYR** = checklist (item, uang, level XP) dengan tanda hijau/merah. **%** = chance akhir (dasar + booster + event). **OUT** = preview hasil. **GO** merah sampai semua syarat terpenuhi, lalu hijau dengan glow.
- **BST** = booster (tambah persen, 1 item terpakai per percobaan). **PRT** = proteksi (hanya terpakai bila menyelamatkan item yang akan hancur). Keduanya didefinisikan di file stasiun.
- Jika gagal menghancurkan item dan tidak ada proteksi, muncul menu konfirmasi (`guis/confirm.yml`, bisa dimatikan lewat `upgrade.confirm-destroy`).
- Semua layout adalah data di `guis/*.yml` (slot, tema, material, `item-model`, `custom-model-data`). File yang rusak dilaporkan di console (file, key, nilai) dan diganti layout bawaan.

### Alur upgrade dan animasi

1. Tombol GO ditekan: GUI dikunci, syarat dicek ulang, cost dikonsumsi, **chance di-roll sekali**.
2. Hasil dan semua sisa item ditulis ke `pending-rewards/` (async), baru animasi mulai.
3. Title `Upgrade Sedang Berlangsung!!` dan subtitle `1%`...`100%`, 9 panel progress terisi, border berputar warna, bunyi chime naik.
4. Di 100%: item diberikan, efek `on-success` / `on-fail-effects` dijalankan, `logs/upgrades.log` ditulis, event ditembakkan, file pending dihapus.

Kasus khusus: GUI ditutup saat animasi (animasi lanjut, hasil tetap datang), pemain disconnect atau server stop (hasil diberikan seketika), crash (file pending dikirim saat join berikutnya), reload saat animasi (memakai snapshot).

### Format recipe

```
MMOITEM:SWORD:X + MONEY:5000 + LEVELS:5 + MMOITEM:MISCELLANEOUS:SHARD:3 + ITEM:DIAMOND:2 => MMOITEM:SWORD:Y (80%)
```
`MONEY` lewat Vault, `LEVELS`, `MMOITEM:TYPE:ID[:jumlah]` dan `ITEM:MATERIAL[:jumlah]` masuk ke slot rune. `on-fail: KEEP|DESTROY`.
Penting: item cost **tidak lagi diambil dari inventory**, pemain harus menaruhnya di slot rune.

## Konfigurasi

`config.yml`: `gui-animation.*` (durasi, interval, title, subtitle, progress bar, suara, `idle-shimmer`, `close-after-result`), `upgrade.*`, `sounds.*`, `hooks.*`.
Override durasi per stasiun/path: `animation: { duration-ticks: 160 }` di `upgrades/*.yml`.
Teks: `lang/en.yml` dan `lang/id.yml` (`language: id`). Di `guis/*.yml`, `{gui.some.key}` diambil dari file bahasa dan `%placeholder%` PlaceholderAPI didukung.

## Perintah & permission

| Perintah | Permission |
|---|---|
| `/itemupgrade <file> [path]` | `octammo.upgrade` (default true), opsional `octammo.upgrade.<file>` |
| `/octammo reload\|update\|upgrade\|list` | `octammo.admin` |
| lewati auto-update | `octammo.update.bypass` |

## Hook opsional

Vault (Excellent Economy ikut lewat Vault), WorldGuard (`allowed-regions` / `blocked-regions`), Floodgate (tap item langsung masuk GUI untuk Bedrock), PlaceholderAPI: `%octammo_upgrade_success%`, `%octammo_upgrade_fail%`, `%octammo_upgrade_total%`, `%octammo_upgrade_rate%`, `%octammo_items_updated%`.

## API

```java
OctaMMOAPI api = Bukkit.getServicesManager().load(OctaMMOAPI.class);
api.openUpgrade(player, "corrupted-sword");            // menu path atau forge
api.openUpgrade(player, "corrupted-sword", "corrupted-sword-1");
api.isAnimating(player);
api.findRecipe("corrupted-sword", itemStack);
api.isOutdated(itemStack);
api.updatePlayer(player);
```
Event: `UpgradePreEvent` (cancel / ubah chance), `UpgradeAnimationStartEvent` (cancel / ubah `durationTicks`), `UpgradeAnimationEndEvent`, `UpgradeResultEvent`, `ItemUpdateEvent`.

## Batasan

- Hasil upgrade adalah item baru dari template; gem / level upgrade MMOItems dari item lama tidak dibawa.
- Item Update hanya memindai inventory pemain (bukan ender chest / container).
- Excellent Economy lewat Vault; mata uang ganda native belum ada.
- Mode creative ditolak (rawan duplikasi di GUI kustom), bisa dibuka dengan `upgrade.allow-creative`.
- Belum ada modul lisensi.
