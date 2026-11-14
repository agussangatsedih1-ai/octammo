# OctaMMO 1.2.0-SNAPSHOT GUI Interaction Hotfix

This source revision addresses the reported GUI input and compatibility problems.

## Changes

- Enchanting and Item Fixer now accept click-to-place, click-to-take, drag-and-drop into the input slot, shift-click from the player inventory, number-key swaps, and offhand swaps.
- GUI control and decoration slots remain protected from item movement.
- Cursor updates use the active InventoryView instead of deprecated InventoryClickEvent cursor mutation.
- Enchanting and Item Fixer now have configurable glass-pane frames.
- GUI titles, decoration material/name/lore, and key slot positions are stored in `plugins/OctaMMO/guis/enchanting.yml` and `plugins/OctaMMO/guis/item-fixer.yml`.
- Enchant entries display MMOItems TYPE/ID detection when MMOItems is installed.
- AdvancedEnchantments `applies` rules are checked against both Bukkit material and MMOItems type/ID.
- Incompatible enchantments are displayed in red and blocked from being applied. Vanilla conflicts use Bukkit's conflict API; AE conflicts use the `conflicts` setting in enchant definitions.

## Build

Run from the project root:

```bash
mvn clean test package
```

## Verification status

- YAML files parsed successfully.
- Maven POM files parsed successfully.
- Java delimiter/syntax-shape scan completed without unmatched braces/brackets.
- A full Maven compile and live Paper-server test were not run in the build environment. Do not treat this ZIP as a server-tested release until it compiles and is tested with the exact Paper, MMOItems, MythicLib, AdvancedEnchantments, and ExcellentEconomy versions used by the server.
