# Changelog

## [1.2.0-SNAPSHOT] - 2026-11-14

### Added
- Added a six-row Custom Enchanting GUI that replaces enchanting-table interaction when enabled.
- Added vanilla enchantment discovery and item-applicability filtering through the Bukkit registry.
- Added AdvancedEnchantments group shortcuts for Simple, Unique, Elite, Ultimate, Legendary, Fabled, and Heroic tiers.
- Added a paged Enchantment Guide that displays custom enchantment descriptions, tiers, maximum levels, and applicability rules.
- Added chat-based enchantment search, tier cycling, page controls, and search reset behavior.
- Added per-session AE level tracking so applied custom enchantments advance correctly even when an AE build exposes no readable enchant map.
- Added support for modern nested `Enchantments/<folder>/enchantments.yml` definitions and item compatibility filtering through AE `applies` rules.
- Added AdvancedEnchantments integration through the documented `AEAPI.applyEnchant` method.
- Added experience-level costs for vanilla and AdvancedEnchantments application.
- Added single-item enchanting safeguards for stackable inputs and safe cursor-stack splitting.
- Added Combine/Crafting input type validation and partial-stack insertion to prevent wrong ingredients from being accepted.
- Added `/octammo combine` with recipe-defined vanilla and MMOItems ingredients/results.
- Added `/octammo crafting` with recipe-defined ingredients/results and optional ExcellentEconomy custom-currency costs.
- Added a native ExcellentEconomy currency bridge using its Bukkit service API instead of relying only on Vault's primary currency.
- Added `/octammo fixer` with MMOItems template rebuilding, vanilla-enchantment restoration, and AdvancedEnchantments book extraction when enchant data can be read.
- Added MMOItems native Revision/Reforger integration for Item Fixer lore reconstruction, with the old lore transfer explicitly disabled.
- Added preservation of supported MMOItems data according to `item-revision.keep-data`, including returning reforge outputs such as removed gems.
- Set the Item Fixer permission to default true for consistency with the other player-facing modules; administrators can restrict it through permissions.
- Added module toggles and feature permissions for Custom Enchanting, Combine, Crafting, and Item Fixer.
- Added working vanilla-material sample recipes for both Combine and Crafting, plus MMOItems and ExcellentEconomy configuration examples.
- Added English recipe files, GUI messages, README, and this changelog.

### Changed
- Updated the Maven project version to `1.2.0-SNAPSHOT`.
- Updated command help, plugin metadata, and dependency declarations.
- Standardized shipped language/configuration resources to English-only.
- Kept the existing Altar Forge implementation and public API version 2 contracts.

### Important behavior notes
- Item Fixer repair uses MMOItems native data-transfer options while regenerating lore; extraction intentionally creates a clean Type/ID template and can reset item-specific data.
- The Item Fixer has not been runtime-tested against every MMOItems/AdvancedEnchantments release; test on copies of production items before enabling player use.
- AdvancedEnchantments extraction uses an ItemStack-based getter if the installed build exposes one and otherwise falls back to matching configured enchantment IDs/display names against item lore. Validate extraction against the exact AdvancedEnchantments build installed on the server.
- Extracted custom enchant books are created as `ENCHANTED_BOOK` items using `AEAPI.applyEnchant`, matching the standard AdvancedEnchantments custom-book item type.
- Run a staging-server test before production deployment.
