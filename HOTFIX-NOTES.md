# OctaMMO 1.2.0-BETA Hotfix Notes

## Startup identity

- Prints a static, five-line OCTAMMO wordmark during enable.
- Shows the resolved plugin version, Bukkit/Paper server version, and optional-hook status.
- The banner can be disabled or edited through `startup-banner` in `config.yml`.
- No animation, repeating task, or delayed startup effect is used.

## Enchanting and Item Fixer

- Input click, shift-click, number-key, offhand, and drag operations apply after the inventory event has completed.
- The enchant search supports Paper chat input and `/octammo search <keyword>` after clicking Search in the GUI. The pending state survives external inventory closure, expires after one minute, and returns input on cancel, timeout, quit, new session, or shutdown.
- Search updates the displayed list, resets the page, and restores the GUI if another chat handler closed it.
- Enchant compatibility is checked against the inserted material, MMOItems Type/ID, AE `applies` rules, current Vanilla enchant conflicts, and configured AE conflicts. Incompatible options are red and cannot be applied.
- Extraction requests each enchanted book from AdvancedEnchantments using the configured `/ae give` command. OctaMMO captures and returns the item AE actually generated instead of manufacturing a replacement book.
- A failed book-generation step leaves the original MMOItems input untouched.

## Combine and Crafting

- Recipe index and detail screens use configurable tinted-glass frames and separate visual accents for each workstation.
- Recipe, page and item actions use short anvil sounds. Successful processing uses the anvil-use sound. Sound type, volume and pitch are configurable in the two workstation GUI YAML files.
- No animated GUI tasks or repeating inventory updates are used.
- Ingredient insertion, hotbar and offhand handling are deferred until after the inventory event and revalidated before moving stacks.

## Configuration

- Enchanting, Item Fixer, Combine and Crafting layouts are separate files in `plugins/OctaMMO/guis/`.
- Startup banner, enchanting search timeout, and the AdvancedEnchantments book command are configurable in `config.yml`.
- All shipped messages, GUI labels, lore, configuration comments and documentation are English.

## Verification boundary

YAML and Maven descriptor validation can be performed on the source bundle. A full Maven compile and a live server test still must be run with the actual Paper, MMOItems, MythicLib, AdvancedEnchantments and ExcellentEconomy versions used by the target server. This source is a BETA build, not a claim of a server-certified release.
