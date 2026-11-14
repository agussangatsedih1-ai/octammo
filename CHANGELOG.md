# Changelog

## [1.2.0-SNAPSHOT] - 2026-11-14

### Added
- Added a six-row Custom Enchanting GUI that replaces enchanting-table interaction when enabled.
- Added vanilla enchantment discovery and item-applicability filtering through the Bukkit registry.
- Added AdvancedEnchantments group shortcuts for Simple, Unique, Elite, Ultimate, Legendary, and Fabled tiers.
- Added support for modern nested `Enchantments/<folder>/enchantments.yml` definitions and item compatibility filtering through AE `applies` rules.
- Added AdvancedEnchantments integration through the documented `AEAPI.applyEnchant` method.
- Added experience-level costs for vanilla and AdvancedEnchantments application.
- Added single-item enchanting safeguards for stackable inputs.
- Added `/octammo combine` with recipe-defined vanilla and MMOItems ingredients/results.
- Added `/octammo crafting` with recipe-defined ingredients/results and optional ExcellentEconomy custom-currency costs.
- Added a native ExcellentEconomy currency bridge using its Bukkit service API instead of relying only on Vault's primary currency.
- Added `/octammo fixer` with MMOItems template rebuilding, vanilla-enchantment restoration, and AdvancedEnchantments book extraction when enchant data can be read.
- Added a safety check that blocks Item Fixer rebuilding if AdvancedEnchantments is installed but its definitions cannot be loaded.
- Added module toggles and feature permissions for Custom Enchanting, Combine, Crafting, and Item Fixer.
- Added English recipe files, GUI messages, README, and this changelog.

### Changed
- Updated the Maven project version to `1.2.0-SNAPSHOT`.
- Updated command help, plugin metadata, and dependency declarations.
- Standardized shipped language/configuration resources to English-only.
- Kept the existing Altar Forge implementation and public API version 2 contracts.

### Important behavior notes
- Item Fixer rebuilds a pristine MMOItems template by Type/ID. It does not preserve random stats, gems, socket state, durability, or arbitrary per-instance data.
- AdvancedEnchantments extraction uses a detected ItemStack-based public getter where present and falls back to definition-name/ID matching against lore. Validate extraction against the exact AdvancedEnchantments build installed on the server.
- Run a staging-server test before production deployment.
