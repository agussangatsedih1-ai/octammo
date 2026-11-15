# OctaMMO 1.2.0-BETA

This BETA update adds a configurable startup banner, a command fallback for enchanting search, authentic AdvancedEnchantments book extraction through AE's own give command, and separate glass-pane GUI layouts for Combine and Crafting. Recipe interactions and item movement are scheduled safely on the main server thread; no GUI animation loops are used.

OctaMMO is a modular Paper plugin for MMOItems servers. Version `1.2.0-BETA` expands the original Altar Forge and public API with a custom enchanting interface, recipe-driven Combine and Crafting Stations, and an MMOItems Item Fixer.

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
octammo-plugin/target/OctaMMO-1.2.0-BETA.jar
```

The jar shades the `octammo-api` module. The API classes are included in the plugin jar and are not shaded from third-party plugins.

## Installation

1. Install Paper 1.21.11 and Java 21.
2. Install MMOItems and MythicLib for MMOItems functionality.
3. Optionally install AdvancedEnchantments and/or ExcellentEconomy before OctaMMO.
4. Place `OctaMMO-1.2.0-BETA.jar` in the server's `plugins` directory and restart.
5. Review `plugins/OctaMMO/config.yml`, `recipes/combine.yml`, and `recipes/crafting.yml`.
6. Restart or run `/octammo reload` after editing plugin configuration files.

The first start creates the default configuration, GUI layouts, language file, and recipe files. Existing config values are retained during migration; newly introduced keys are appended and the previous file is backed up under `plugins/OctaMMO/backups/`. When an older development build left `recipes/combine.yml` or `recipes/crafting.yml` with an empty `recipes:` section, OctaMMO backs up that empty file and seeds the working example recipe. Non-empty recipe files are never overwritten.

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
| `/octammo fixer` | `octammo.fixer` | true | Open the MMOItems Item Fixer |
| `/octammo enchanting` | `octammo.enchant` | true | Open the custom enchanting GUI |
| `/octammo search <keyword>` | `octammo.enchant` | true | Submit a search while the enchanting GUI is waiting for input |

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

When enabled, interacting with an enchanting table opens a six-row, 54-slot OctaMMO GUI. The input slot is on the left; enchantment entries are displayed on the right. Its bottom controls provide keyword search, tier filtering, a paged enchantment guide, previous/next page navigation, input return, and close. The tier filter cycles through All Tiers, Vanilla, Simple, Unique, Elite, Ultimate, Legendary, Fabled, and Heroic; right-clicking the filter resets it to All Tiers. Search is entered in chat after clicking the spyglass. The pending search state survives a chat plugin closing the inventory, expires after 60 seconds, and returns the input item on cancellation, timeout, quit, or shutdown. If a chat-formatting plugin blocks the captured chat event, use `/octammo search <keyword>` after clicking the search icon. The guide opens a separate paged catalogue with each loaded custom enchantment's description, group, maximum level, and supported item categories.

Vanilla enchantments are discovered through the Bukkit enchantment registry and filtered by `Enchantment.canEnchantItem` for the inserted item. AdvancedEnchantments definitions are read recursively from the plugin's `Enchantments/`, `enchantments/`, or `enchants/` folders, including the modern `Enchantments/<folder>/enchantments.yml` layout. Definitions are grouped by their configured `group` value and filtered against each enchantment's `applies` material/category list (including shortcuts such as `ALL_SWORD`, `ALL_PICKAXE`, `ALL_SPADE`, and `ALL_ARMOR`). The GUI tiers therefore need to match the group names configured on your AE enchantments (for example `SIMPLE`, `UNIQUE`, `ELITE`, `ULTIMATE`, `LEGENDARY`, `FABLED`, and `HEROIC`). Custom enchantments are applied through the documented `AEAPI.applyEnchant(String, int, ItemStack)` method.

A normal click applies the next level; shift-click applies the configured maximum level. Enchanting takes one item per operation; stackable inputs are split so XP is never charged for an entire stack at once. Costs are charged in player experience levels:

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
      heroic: 10
```

Changing enchantment definitions or AE configuration requires `/octammo reload` or a restart. The GUI only lists an enchantment group when the item is inserted. Each refresh identifies the current item as MMOItems `TYPE/ID` or a vanilla/non-MMOItems item. Vanilla applicability is checked by Bukkit, including Vanilla conflict rules such as Sharpness versus Smite. AdvancedEnchantments applicability is checked against the installed AE `applies` rules and MMOItems identity; configured AE conflicts are also checked. Incompatible entries are shown in red and cannot be applied.

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

Replace the sample Type and Item ID values with IDs that exist on your server. The shipped `combine.yml` contains a working vanilla-material example so the menu is usable immediately. Replace or extend it with MMOItems recipes using the exact Type and Item ID values from your server.

### 4. Crafting Station

Open with `/octammo crafting`. Recipes are declared in `recipes/crafting.yml` and use the same ingredient/result format as Combine. Each crafting recipe may also charge a native ExcellentEconomy currency:

```yaml
currency:
  id: octacoin
  amount: 1000
```

`id` must be the actual ExcellentEconomy currency ID, not its display name or symbol. OctaMMO uses ExcellentEconomy's service API and currency-specific `getBalance(Player, String)` / `withdraw(Player, String, double)` operations. It does not route these recipe costs through Vault's primary economy provider.

The workstation GUIs use separate colored-glass frames and static recipe/output panels. Their select, insert, fail, and success anvil sounds can be changed in `guis/combine.yml` and `guis/crafting.yml`. No GUI animation loop is used. The shipped `crafting.yml` contains a working vanilla-material example with no currency requirement, so the menu is usable immediately. Add the optional currency section to any recipe after replacing `octacoin` with an existing currency ID. If the currency is not registered or the provider is unavailable, the craft is refused and the ingredients are not consumed.

### 5. MMOItems Item Fixer

Open with `/octammo fixer`. The GUI provides two operations:

**Extract AE Enchants**

- Requires an MMOItems item and an active AdvancedEnchantments integration.
- Attempts to read the custom enchant map from an ItemStack-based public AE API getter. If the installed AE API does not expose such a getter, the fixer falls back to matching the enchant definition names/IDs against item lore.
- Rebuilds the base MMOItems template from the original `MMOITEMS_ITEM_TYPE` and `MMOITEMS_ITEM_ID` tags.
- Produces a clean base item; random stats, sockets, upgrades, and other instance-specific values are not guaranteed to be retained by extraction.
- Requests each extracted book from AdvancedEnchantments through its documented `/ae give <player> <enchantment> <level>` command. OctaMMO captures the exact item AdvancedEnchantments gives, including the book material, NBT/PDC, lore, success rate, destroy rate, and version-specific metadata configured by the server. It does not manufacture a substitute enchanted book.
- Does not consume the input if the item, enchantments, template, or book output cannot be prepared.

**Rebuild Item Lore**

- Uses MMOItems' native `MMOItemReforger` and `ReforgeOptions` API to rebuild the existing item from its current Type/ID template.
- Forces the old lore-transfer option off so corrupted lore is regenerated from the current MMOItems template.
- Preserves other supported item data according to the server's `item-revision.keep-data` settings, including enchantments, upgrades, gemstones, skins, soulbound state, tier, and AdvancedEnchantments when those options are enabled and supported by the installed MMOItems version.
- Restores vanilla enchantments and re-applies readable AdvancedEnchantments enchants when the AE definitions can be read.
- Returns extra items reported by MMOItems' reforge pipeline (for example, gems that no longer fit the refreshed item).
- Does not reroll random stats unless the MMOItems keep-data configuration requests it.

**Important data behavior:** Item Fixer uses MMOItems' own data-transfer pipeline rather than copying arbitrary NBT or taking a new pristine template. The old lore is intentionally not copied; other values follow MMOItems' `item-revision.keep-data` options. The separate **Extract AE Enchants** action deliberately creates a clean base template and extracted enchanted books, so item-instance data such as gems, upgrades, and random stats may not survive extraction. Test both operations on copies before enabling the fixer for players. The fixer processes one item at a time.

### 6. Startup Banner

The enable banner prints a static OCTAMMO ASCII wordmark, the resolved plugin version, the server version, and a summary of optional integrations. Configure it under `startup-banner` in `config.yml`. It does not use animations or repeating tasks.

### 7. MMOItems Interaction Lock

When enabled, the interaction listener prevents MMOItems items in the main/off hand from placing blocks, breaking blocks, tilling farmland, or creating dirt paths. The behavior is controlled by `interact-lock:` in `config.yml`; players with `octammo.interact.bypass` are exempt.

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Module toggles, enchanting costs, fixer behavior, interaction lock, Altar Forge settings |
| `lang/en.yml` | English chat messages and GUI text |
| `guis/*.yml` | Enchanting, Item Fixer, Combine, Crafting, and Altar Forge GUI titles, decoration, slot layouts, and workstation sounds |
| `recipes/combine.yml` | Combine recipes |
| `recipes/crafting.yml` | Crafting recipes and currency costs |
| `upgrades/*.yml` | Altar Forge stations and paths |

All shipped configuration comments, messages, GUI labels, lore, and documentation are in English.

## Developer API

The public API is provided by the `octammo-api` Maven module and is included in the plugin jar. For another plugin project, install the API module locally:

```bash
mvn -pl octammo-api install
```

Then use it as a `provided` dependency with version `1.2.0-BETA` and declare OctaMMO as a soft dependency. The existing API version is `2`; this release keeps its current method/event contracts.

## Validation status for this source package

- YAML resource parsing and Maven descriptor parsing are checked before packaging.
- Java source syntax is checked separately from dependency-backed compilation.
- Maven compilation and JUnit execution must be run with JDK 21 and Maven against the configured Paper and optional-plugin repositories.
- Live Paper-server integration still requires testing against the exact Paper, MMOItems, AdvancedEnchantments, and ExcellentEconomy builds installed on your server.

The GitHub Actions workflow runs `mvn -B clean test package` on pushes and pull requests. Treat the plugin as a staging build until that workflow or a local Maven build succeeds and the GUI operations have been tested on a copy of your server data.

## Notes and testing

- Always test recipe and fixer behavior on a staging server with copies of production items.
- AdvancedEnchantments has version-specific behavior. OctaMMO uses the documented `AEAPI.applyEnchant` method for applying custom enchantments. The GUI reads each definition's `group`, `levels`, `applies`, and `conflicts` fields. Extraction reads enchant levels through the supported integration path and asks AdvancedEnchantments to create each genuine enchanted book with the configured `/ae give` command.
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


## Feature GUI configuration

The following files are generated in `plugins/OctaMMO/guis/` and can be edited without rebuilding the plugin:

- `enchanting.yml` — title, glass-pane decoration, input/control slots, and enchantment grid slots.
- `item-fixer.yml` — title, glass-pane decoration, and input/action slots.
- `combine.yml` — Combine Forge title, glass-pane palette, recipe slots, input slots, and action slots.
- `crafting.yml` — Crafting Atelier title, glass-pane palette, recipe slots, input slots, and action slots.
- `default-upgrade.yml`, `path-select.yml`, and `confirm.yml` — Altar Forge layouts.

All slot values use the Bukkit inventory index range `0-53`. Keep interactive slots unique and do not overlap the enchantment grid with the control slots.

The enchanting input supports normal cursor placement, drag-and-drop into the configured input slot, shift-click from the player inventory, number-key swaps, and offhand swaps. Search is entered in chat after clicking the search icon; `/octammo search <keyword>` is available as a command fallback while a search prompt is active.

Crafting and Combine use separate configurable GUI layouts. Both use static glass-pane frames and decorative workbench sections; no per-tick GUI animation or repeating animation task is scheduled. Ingredient insertion, recipe selection, and successful processing use short anvil sounds.

Enchantment entries are checked against the inserted item's Bukkit material and, when MMOItems is installed, its MMOItems `TYPE/ID`. Incompatible entries are shown in red and cannot be applied. Vanilla enchantment conflicts are checked using Bukkit's enchantment conflict rules; AdvancedEnchantments conflicts are read from each enchantment's `conflicts` setting when present.
