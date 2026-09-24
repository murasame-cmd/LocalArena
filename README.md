# LocalArena

LocalArena is an offline-first Android collection of local multiplayer tabletop games.

## Current games
- Chess
- Sea Battle
- Durak

## Platform direction
The app is intentionally structured as a small game platform rather than a single-game app. New games can be registered through `GameCatalog` and surfaced by the common shell.

## Monetization-ready architecture
Monetization content is **not enabled yet**. The current build prepares the product for later additions:
- Store screen for themes/skins and future digital content.
- Local profile/preferences for selected theme, skin and future entitlements.
- Stable IDs for cosmetic/content items.
- A dedicated `MonetizationState` layer so billing/ads can be added without rewriting game logic.
- Figma screens for Store, Settings and the game-library shell.

Actual paid content, ads and purchase flows will be added later. Google Play one-time products can support permanent non-consumable unlocks such as premium themes or an ad-free version. citeturn0search0

## Local multiplayer
Two phones play over the same local network without internet during the match. TCP is used for the MVP.

## Build
GitHub Actions builds a debug APK on pushes to `main`.
