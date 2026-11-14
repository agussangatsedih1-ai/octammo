# OctaMMO 1.2.0-SNAPSHOT

OctaMMO is a modular Paper plugin for MMOItems servers. Version `1.2.0-SNAPSHOT` expands the original Altar Forge and public API with a custom enchanting interface, recipe-driven Combine and Crafting Stations, and an MMOItems Item Fixer.

## Compatibility

| Component | Requirement |
|---|---|
| Minecraft server | Paper 1.21.11 |
| Java runtime | Java 21 |
| MMOItems | Required for Altar Forge, MMOItems recipe ingredients/results, and Item Fixer |
| MythicLib | Required by MMOItems integration |
| AdvancedEnchantments | Optional; required for custom-enchantment browsing, application, and extraction |
| ExcellentEconomy | Optional; required only by recipes that charge a configured ExcellentEconomy currency |
| Vault | Optional; continues to support the existing Altar Forge money cost |
| PlaceholderAPI | Optional |
| WorldGuard | Optional |
| Floodgate | Optional; Bedrock players are not supported by the custom GUI flows |

Optional integrations are loaded through soft dependencies or reflection. OctaMMO can still start when AdvancedEnchantments or ExcellentEconomy is not installed; the related features will report that the integration is unavailable when used.

## Build

Requirements: JDK 21 and Apache Maven 3.9 or newer.

```bash
mvn -B clean test package
```

The plugin jar is generated at:

```text
octammo-plugin/target/OctaMMO-1.2.0-SNAPSHOT.jar
```

The jar shades the `octammo-api` module. The API classes are included in the plugin jar and are not shaded from third-party plugins.

## Installation

1. Install Paper 1.21.11 and Java 21.
2. Install MMOItems and MythicLib for MMOItems functionality.
3. Optionally install AdvancedEnchantments and/or ExcellentEconomy before OctaMMO.
4. Place `OctaMMO-1.2.0-SNAPSHOT.jar` in the server's `plugins` directory and restart.
5. Review `plugins/OctaMMO/config.yml`, `recipes/combine.yml`, and `recipes/crafting.yml`.
6. Restart or run `/octammo reload` after editing plugin configuration files.

The first start creates the default configuration, GUI layouts, language file, and recipe files. Existing config values are retained during migration; newly introduced keys are appended and the previous file is backed up under `plugins/OctaMMO/backups/`.

**Language files are English-only in this release.** If you are upgrading from an older version, change `language: en` in `config.yml`. The old `lang/id.yml` file is no longer used.

## Commands and permissions

| Command | Permission | Default | Description |
|---|---|---:|---|
| `/octammo help` | `octammo.use` | true | Show command help |
| `/octammo version` | none | all | Show plugin version and authors |
| `/octammo reload` | `octammo.admin` | op | Reload supported configuration files |
| `/octammo list` | `octammo.admin` | op | List Altar Forge stations |
| `/octammo upgrade [station] [player] [path]` | `octammo.upgrade` | true | Open an Altar Forge station |
| `/itemupgrade [station] [path]` | `octammo.upgrade` | true | Shortcut for Altar Forge |
| `/octammo combine` | `octammo.combine` | true | Open the Combine Station |
| `/octammo crafting` | `octammo.crafting` | true | Open the Crafting Station |
| `/octammo fixer` | `octammo.fixer` | op | Open the MMOItems Item Fixer |
| `/octammo enchanting` | `octammo.enchant` | true | Open the custom enchanting GUI |

Right-clicking an enchanting table also opens the custom enchanting GUI when `modules.custom-enchanting` is enabled. The aliases `/octa` and `/omm` are available for `/octammo`.

Additional permissions:

- `octammo.upgrade.<station-id>` can be required per station when `upgrade.require-station-permission` is enabled.
- `octammo.upgrade.*` is available for permission managers that grant all station permissions.
- `octammo.interact.bypass` bypasses the MMOItems interaction lock.

## Modules

### 1. Altar Forge — Item Upgrade

The existing 54-slot Altar Forge, configurable stations and paths, upgrade requirements, runes, boosters, protection items, result chances, loading sequence, pending rewards, audit log, and public API are retained.

- One YAML file in `upgrades/` defines a station; each `paths:` entry defines a path.
- See `upgrades/example-sword.yml` for the supported station schema.
- GUI layouts are located in `guis/`.
- Item-upgrade behavior is enabled by `modules.item-upgrade`.

The existing public API remains at API version 2. Existing integrations using `OctaMMO.getApi()` or the Bukkit Services Manager can continue to use the published API types.

### 2. Custom Enchanting

When enabled, interacting with an enchanting table opens a six-row, 54-slot OctaMMO GUI. The input slot is on the left; enchantment entries are displayed on the right. The bottom row contains tier shortcuts for:

- Vanilla
- Simple
- Unique
- Elite
- Ultimate
- Legendary
- Fabled

Vanilla enchantments are discovered through the Bukkit enchantment registry and filtered by `Enchantment.canEnchantItem` for the inserted item. AdvancedEnchantments definitions are read recursively from the plugin's `Enchantments/`, `enchantments/`, or `enchants/` folders, including the modern `Enchantments/<folder>/enchantments.yml` layout. Definitions are grouped by their configured `group` value and filtered against each enchantment's `applies` material/category list (including shortcuts such as `ALL_SWORD` and `ALL_ARMOR`). The GUI tiers therefore need to match the group names configured on your AE enchantments (for example `SIMPLE`, `UNIQUE`, `ELITE`, `ULTIMATE`, `LEGENDARY`, and `FABLED`). Custom enchantments are applied through the documented `AEAPI.applyEnchant(String, int, ItemStack)` method.

Normal click applies level I. Shift-click applies the configured maximum level. Enchanting takes one item per operation; stackable inputs are split so XP is never charged for an entire stack at once. Costs are charged in player experience levels:

```yaml
enchanting:
  costs:
    vanilla-per-level: 1
    advanced:
      simple: 2
      unique: 3
      elite: 4
      ultimate: 5
      legendary: 6
      fabled: 8
```

Changing enchantment definitions or AE configuration requires `/octammo reload` or a restart. The GUI only lists an enchantment group when the item is inserted. Vanilla applicability is checked by Bukkit. AdvancedEnchantments applicability remains governed by the installed AE configuration and its own API behavior.

### 3. Combine Station

Open with `/octammo combine`. Recipes are declared in `recipes/combine.yml`. A recipe may use up to six input slots and can consume vanilla materials, MMOItems identified by `TYPE/ID`, or a mixture of both. The recipe result can also be a vanilla material or an MMOItem.

Example:

```yaml
recipes:
  reforged_blade:
    name: "Reforged Blade"
    description:
      - "Combine two fragments into a new weapon."
    ingredients:
      - type: MMOITEMS
        item-type: SWORD
        item-id: BLADE_FRAGMENT
        amount: 2
      - type: MATERIAL
        material: NETHERITE_INGOT
        amount: 1
    result:
      type: MMOITEMS
      item-type: SWORD
      item-id: REFORGED_BLADE
      amount: 1
```

Replace the sample Type and Item ID values with IDs that exist on your server. The shipped `combine.yml` intentionally contains no active MMOItems recipe so it does not assume your server's item IDs.

### 4. Crafting Station

Open with `/octammo crafting`. Recipes are declared in `recipes/crafting.yml` and use the same ingredient/result format as Combine. Each crafting recipe may also charge a native ExcellentEconomy currency:

```yaml
currency:
  id: octacoin
  amount: 1000
```

`id` must be the actual ExcellentEconomy currency ID, not its display name or symbol. OctaMMO uses ExcellentEconomy's service API and currency-specific `getBalance(Player, String)` / `withdraw(Player, String, double)` operations. It does not route these recipe costs through Vault's primary economy provider.

The included `example_netherite_blade` recipe is a sample. Change the currency ID and amount to match your economy, or remove the recipe. If the currency is not registered or the provider is unavailable, the craft is refused and the ingredients are not consumed.

### 5. MMOItems Item Fixer

Open with `/octammo fixer`. The GUI provides two operations:

**Extract AE Enchants**

- Requires an MMOItems item and an active AdvancedEnchantments integration.
- Attempts to read the custom enchant map from an ItemStack-based public AE API getter. If the installed AE API does not expose such a getter, the fixer falls back to matching the enchant definition names/IDs against item lore.
- Rebuilds the base MMOItems template from the original `MMOITEMS_ITEM_TYPE` and `MMOITEMS_ITEM_ID` tags.
- Creates one AdvancedEnchantments enchanted book per detected enchantment, applying the enchantment through AEAPI to an enchanted-book stack.
- Does not consume the input if the item, enchantments, template, or book output cannot be prepared.

**Rebuild Item Lore**

- Rebuilds the original base template using the MMOItems Type and Item ID.
- Restores vanilla enchantments, and readable AdvancedEnchantments enchantments when AE is available.
- Refuses to rebuild when AdvancedEnchantments is installed but its enchantment definitions could not be loaded, to avoid silently stripping known custom-enchantment data.
- Intended for restoring a consistent base item after lore corruption, such as a gemstone removal operation.

**Important data behavior:** the MMOItems API exposes a reliable way to create a fresh template from Type/ID, but this operation is not a full serialization restore of the original instance. Rebuilding the item resets randomized stats, gems/socket contents, custom upgrade state, durability, and other per-instance data unless they are recreated by MMOItems itself. Test this on copies before enabling the fixer for players. The fixer processes one item at a time to avoid ambiguous multi-stack extraction.

### 6. MMOItems Interaction Lock

When enabled, the interaction listener prevents MMOItems items in the main/off hand from placing blocks, breaking blocks, tilling farmland, or creating dirt paths. The behavior is controlled by `interact-lock:` in `config.yml`; players with `octammo.interact.bypass` are exempt.

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Module toggles, enchanting costs, fixer behavior, interaction lock, Altar Forge settings |
| `lang/en.yml` | English chat messages and GUI text |
| `guis/*.yml` | Existing Altar Forge GUI layouts |
| `recipes/combine.yml` | Combine recipes |
| `recipes/crafting.yml` | Crafting recipes and currency costs |
| `upgrades/*.yml` | Altar Forge stations and paths |

All shipped configuration comments, messages, GUI labels, lore, and documentation are in English.

## Developer API

The public API is provided by the `octammo-api` Maven module and is included in the plugin jar. For another plugin project, install the API module locally:

```bash
mvn -pl octammo-api install
```

Then use it as a `provided` dependency with version `1.2.0-SNAPSHOT` and declare OctaMMO as a soft dependency. The existing API version is `2`; this release keeps its current method/event contracts.

## Notes and testing

- Always test recipe and fixer behavior on a staging server with copies of production items.
- AdvancedEnchantments has version-specific behavior. OctaMMO uses the documented `AEAPI.applyEnchant` method for applying custom enchantments. The GUI reads each definition's `group`, `levels`, and `applies` fields; extraction additionally depends on the data-access method exposed by the installed AE build or the lore fallback.
- ExcellentEconomy currency transactions require the currency to exist and operations not to be locked by ExcellentEconomy.
- This source bundle was prepared without Maven installed or dependency repository access in the build environment. Run the Maven command above or use the included GitHub Actions workflow to perform the authoritative dependency-backed compile and test.


## External API references

- [AdvancedEnchantments — Plugin API](https://ae.advancedplugins.net/for-developers/plugin-api)
- [AdvancedEnchantments — Creating Enchantments](https://ae.advancedplugins.net/enchantments/creating-enchantments)
- [AdvancedEnchantments — Custom Effects](https://ae.advancedplugins.net/for-developers/create-custom-effects)
- [AdvancedEnchantments — Custom Triggers](https://ae.advancedplugins.net/for-developers/create-custom-effects-1)
- [AdvancedEnchantments — Custom Targets](https://ae.advancedplugins.net/for-developers/create-custom-effects-2)
- [AdvancedEnchantments — Plugin Compatibility Issues](https://ae.advancedplugins.net/for-developers/plugin-compatiblity-issues)
- [ExcellentEconomy — Developer API](https://nightexpressdev.com/excellenteconomy/utility/developer-api/)
