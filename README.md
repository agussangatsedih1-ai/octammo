# OctaMMO 1.0.2

Add-on MMOItems untuk **Paper 1.21.11** (Java 21, hanya pemain Java). Dua modul yang bisa dimatikan sendiri-sendiri di `config.yml` (`modules:`), ditambah API publik.

- **Item Update**: item MMOItems yang sudah kadaluarsa (habis nerf/buff) diganti, gratis, tanpa `revision-id`.
- **Item Upgrade**: stasiun "Altar Forge" (GUI 6 baris). Setelah tombol ditekan GUI **ditutup**, title menghitung 1% sampai 100%, hasil diberikan, GUI dibuka lagi. Tidak ada animasi di dalam GUI.
- **API**: modul `octammo-api` terpisah.

## Struktur proyek

```
octammo-parent (pom)
 ├─ octammo-api      interface + event saja (compileOnly untuk developer lain)
 └─ octammo-plugin   implementasi, jar plugin sudah menyertakan class API
```

## Instalasi

1. Pasang **MMOItems** dan **MythicLib**. Tanpa keduanya plugin tetap load, kedua modul nonaktif dan console menampilkan satu peringatan.
2. Build dengan `mvn clean package` (atau GitHub Actions, lihat `.github/workflows/build.yml`). Hasil: `octammo-plugin/target/OctaMMO-1.0.2.jar`.
3. Taruh jar di `plugins/`, restart. Soft-depend opsional: Vault, PlaceholderAPI, Floodgate, WorldGuard, Excellent Economy (lewat Vault).

Setelah update plugin, key config baru ditambahkan otomatis tanpa menyentuh nilaimu; file lama disalin ke `backups/`.

## Command dan permission

| Command | Permission | Default |
|---|---|---|
| `/octammo help`, `/octammo version` | `octammo.use` | semua |
| `/octammo update hand` / `inventory` | `octammo.update` | **op** |
| `/octammo update <player\|all>` | `octammo.update.others` | op |
| `/octammo upgrade [station] [player] [path]`, `/itemupgrade [station] [path]`, `/octammo:itemupgrade` | `octammo.upgrade` | semua |
| `/octammo reload`, `/octammo list`, buka untuk pemain lain | `octammo.admin` | op |
| item tidak ikut auto-update | `octammo.update.bypass` | tidak ada |
| izin per stasiun | isi `permission:` di file stasiun (misal `octammo.upgrade.example-sword`) | tidak ada |

Alias: `/octa`, `/omm`. `/itemupgrade` tanpa argumen membuka daftar stasiun yang boleh diakses pemain.

## Item Update

**Hanya OP secara default.** Auto-update hanya memproses pemain yang punya `octammo.update`; `update.require-permission: false` membuka untuk semua pemain.

- **Deteksi tanpa revision-id.** Signature (SHA-256) dihitung dari YAML template di `plugins/MMOItems/item/` (kunci diurutkan; `update.ignore-stats` mengecualikan stat tertentu). Signature disimpan di PDC item `octammo:update_signature`. Item dianggap kadaluarsa bila signature-nya berbeda dari template sekarang. Item tanpa signature diperbarui sekali lalu diberi signature (`update.unsigned-items: STAMP` hanya menandainya tanpa mengganti, cocok untuk rollout pertama).
- **Penggantian** lewat console: `mmoitems give {type} {id} {player} {amount}` (`update.give-command`). Stack lama dilepas dulu di tick yang sama, lalu hanya tetap hilang bila item baru terverifikasi masuk inventory atau di-drop di kaki pemain; kalau tidak, semuanya dikembalikan. Pilihan `update.method: API` menaruh item di slot yang sama.
- Pemicu: join, buka inventory (debounce), pickup, interval opsional. Ada cooldown dan batas item per batch. Log audit: `logs/updates.log` (rotasi 2 MB).
- `update.preserve.*` menyalin data lama (soulbound, gems, upgrade level, durability, NBT kustom) sebagai NBT mentah, **best effort**: lore dan stat tidak dihitung ulang, jadi gem/level upgrade bisa tampil keliru. Semuanya default `false`.
- Keterbatasan: `mmoitems give` menghasilkan item baru dari template, jadi data custom lama hilang kecuali disalin lewat opsi di atas. Signature belum memperhitungkan `parent`/inherit di template MMOItems.

## Item Upgrade

Satu file di `upgrades/` = satu **stasiun**; tiap entri di `paths:` = satu **path**. Lihat `upgrades/example-sword.yml` (lengkap, ber-komentar). Stasiun dengan banyak path menampilkan menu path (`guis/path-select.yml`).

### Altar Forge

```
      0     1    2    3    4     5   6    7   8
r0    BACK  ▒    ▒    ▒    INFO  ▒   ▒    ▒   X
r1    ▒     ▒    ▒    C1   C2    C3  ▒    ▒   SYR
r2    ▒     ▒    ▒    C4   IN    C5  →    OUT ▒
r3    ▒     ▒    ▒    C6   C7    C8  ▒    %   ▒
r4    ▒     BST1 BST2 BST3 GO    ▒   PRT  ▒   ▒
r5    G1    G2   G3   G4   G5    G6  G7   G8  G9
```

- **IN** hanya menerima TYPE/ID path itu. **C1-C8** aktif sebanyak slot yang dibutuhkan cost berbentuk item (satu cost besar dipecah ke beberapa slot sesuai ukuran stack); sisanya terkunci. Slot kosong menampilkan placeholder (bertanda PDC `octammo:virtual`, tidak bisa diambil atau dishift-click).
- **SYR** = checklist requirement dan cost (uang, level, custom) dengan tanda hijau/merah. **%** = chance akhir. **OUT** = preview hasil. **GO** merah sampai semua syarat terpenuhi.
- **G1-G9** = gauge chance **statis**: panel terisi sesuai chance, merah di bawah 30%, kuning 30-70%, hijau di atas 70%, hanya diperbarui saat chance berubah.
- Hanya slot yang berubah yang di-update; tidak ada task di dalam GUI.
- Layout, tema, material, `item-model`, `custom-model-data` ada di `guis/*.yml`; file rusak dilaporkan (file, key, nilai) dan diganti layout bawaan.
- Panel merah/hijau per slot rune hanya muncul bila `slots.rune-status` diisi; item asli pemain tidak bisa diberi warna, jadi statusnya selalu terlihat di checklist SYR.

### Proses upgrade

1. GO ditekan: sesi dikunci, semua syarat dicek ulang, uang/level dikonsumsi, **chance di-roll sekali**.
2. GUI dikosongkan dan **ditutup**. Hasil dan semua item yang kembali (sisa stack, input yang selamat, booster berlebih) disimpan di sesi dan ke `pending-rewards/` (ditulis async).
3. Title `Upgrade Sedang Berlangsung!!` dan subtitle `1%` sampai `100%`. **Satu task global** untuk semua pemain, hanya hidup selama ada sesi, title dikirim hanya saat persen berubah (`percent-step` mengurangi paket).
4. Pemain dikunci: efek slowness dan jump negatif (tanpa listener gerak), invulnerable, command/interact/inventory/drop/combat dibatalkan (`upgrade-loading.lock.*`). Listener mengecek `Map` sesi (O(1)) di baris pertama.
5. Di 100%: item diberikan, efek `on-success` / `on-fail-effects` jalan, `logs/upgrades.log` ditulis, event ditembakkan, title hasil tampil, GUI dibuka lagi dalam keadaan kosong.

Pemain disconnect, server stop, atau plugin disable saat loading: hasil diberikan seketika (tanpa title). Bila pemain tidak online, file `pending-rewards` dikirim saat join berikutnya. Reload tidak mengubah sesi yang berjalan.

### Format path

| Bagian | Isi |
|---|---|
| `requirements` | `level >= 30`, `permission node`, `placeholder %x% >= 30` (PlaceholderAPI), atau requirement dari plugin lain |
| `input` | `type`, `id`, `amount`, `consume` |
| `costs` (`kind`) | `MMOITEMS` dan `VANILLA` (masuk slot rune), `MONEY` (`provider`: Vault, Excellent Economy ikut lewat Vault), `LEVELS`, `CUSTOM` / `COMMAND_CHECK` (lewat API) |
| `output` | `mode: SINGLE` atau `WEIGHTED`; `results` berisi `type`, `id`, `amount`, `weight`, `preserve` |
| `chance` | `base`, `max-total`, `booster-slots` (0-3), `booster-consume` (`ALWAYS`/`SUCCESS_ONLY`/`NEVER`), `boosters` |
| `on-fail` | `input`: `KEEP`/`CONSUME`/`DOWNGRADE`/`BREAK`; `costs`: `CONSUME`/`REFUND`/`PARTIAL`; `refund-percent`; `protection`; `downgrade` |
| `on-success`, `on-fail-effects` | `commands` (`console:` atau `player:`), `sound`, `title`, `broadcast`, `effects` (API) |
| stasiun | `display-name`, `permission`, `gui`, `cooldown`, `worlds-blacklist`, `worldguard-regions-blacklist`, `loading.duration-ticks` |

### Booster bertumpuk

Persen **semua** item booster dijumlahkan: `chance = min(max-total, base + jumlah(add × jumlah item))`, lalu bisa diubah `UpgradePreEvent`. Contoh base 75 dengan Lucky Charm +10: 1 item = 85%, 2 = 95%, 3 = 100%. Satu slot boleh berisi stack, item di slot berbeda ikut dijumlahkan, jenis booster berbeda boleh dicampur. Hanya item yang benar-benar dihitung (maksimal `max-items`) yang dikonsumsi; kelebihannya kembali ke pemain.

Proteksi hanya terpakai bila benar-benar menyelamatkan input (`on-fail.input` CONSUME atau DOWNGRADE); `BREAK` mengabaikannya.

WorldGuard: flag `octammo-upgrade` (allow/deny) dan `worldguard-regions-blacklist` per stasiun. Pemain Bedrock (Floodgate) hanya mendapat pesan bahwa GUI tidak didukung.

## Placeholder (PlaceholderAPI)

`%octammo_upgrades_done%`, `%octammo_upgrades_success%`, `%octammo_upgrades_failed%`, `%octammo_can_upgrade_<id>%`, `%octammo_updates_done%`.

## API

Gunakan sebagai `compileOnly` (`mvn install` di `octammo-api`) dan `softdepend: [OctaMMO]`.

```java
OctaMMOApi api = OctaMMO.getApi();   // io.octammo.api.OctaMMO, atau Bukkit.getServicesManager().load(OctaMMOApi.class)
api.openStation(player, "example-sword");
api.attemptUpgrade(player, new UpgradeContext("example-sword", "tier-2"));   // tanpa GUI, item diambil dari inventory
api.needsUpdate(itemStack);
api.isUpgrading(player);
```

Event: `ItemUpdatePreEvent` (cancel), `ItemUpdateEvent`, `UpgradeStationOpenEvent` (cancel), `UpgradePreEvent` (cancel, `setChance`, `getBoosterBonus`), `UpgradeLoadingStartEvent` (cancel, ubah `durationTicks`), `UpgradeLoadingEndEvent`, `UpgradeResultEvent`, `UpgradeCloseEvent`.

Contoh hook (cost, requirement, efek kustom dan event):

```java
public final class MyHook implements Listener {
    @EventHandler
    public void onEnable(PluginEnableEvent e) {
        if (!e.getPlugin().getName().equals("OctaMMO")) return;
        OctaMMOApi api = OctaMMO.getApi();

        api.registerCostType(new CostType() {
            public String id() { return "my-points"; }
            public boolean has(Player p, ConfigurationSection cfg) { return MyPoints.get(p) >= cfg.getInt("amount"); }
            public void take(Player p, ConfigurationSection cfg) { MyPoints.take(p, cfg.getInt("amount")); }
            public void refund(Player p, ConfigurationSection cfg, int percent) { MyPoints.give(p, cfg.getInt("amount") * percent / 100); }
        });
    }

    @EventHandler
    public void onUpgrade(UpgradePreEvent e) {
        if (e.getPlayer().hasPermission("vip.luck")) e.setChance(Math.min(100.0, e.getChance() + 5.0));
    }
}
```
YAML: `- { kind: CUSTOM, id: my-points, amount: 100 }`.

## Belum ada / keterbatasan

- Update isi shulker box, GUI konfirmasi untuk update manual, partikel update, dan database statistik belum dibuat (statistik memakai PDC pemain).
- Excellent Economy dipakai lewat Vault; mata uang ganda native belum ada.
- `min-upgrade-level` di `input` belum didukung.
- Struktur package belum dipecah sesuai usulan (`core`, `update`, `upgrade`, `gui`, `hook`, `api`, `command`, `util` dipakai apa adanya).
- Belum ada modul lisensi.
