# LocalArena

LocalArena is an offline-first Android collection of local multiplayer tabletop games.

## 📲 Скачать APK

**[⬇️ Скачать последнюю версию APK](https://github.com/murasame-cmd/LocalArena/releases/latest)**

Все опубликованные сборки доступны в разделе **Releases**. Для Android скачивай файл `LocalArena-v*.apk` и устанавливай его на телефон.

## Current games
- Chess
- Sea Battle
- Durak

## Product model — base edition

The first release is a complete **free base edition**:
- No ads.
- No artificial limits, lives, energy or waiting timers.
- The core games are fully playable without Premium.
- Local multiplayer does not depend on purchases or monetization state.

Optional monetization is prepared for a later update:
- 💖 **Support the author** — voluntary support; payment flow is not connected in the MVP.
- ⭐ **Premium** — additional skins, themes, effects and other optional extras.
- Premium is not required to remove advertising: the base edition is already ad-free.

Actual payments, billing integration and paid content are intentionally postponed until the base MVP is finished.

## Platform direction
The app is intentionally structured as a small game platform rather than a single-game app. New games can be registered through GameCatalog and surfaced by the common shell.

## Monetization-ready architecture
The current build prepares the product for later additions without changing the core game logic:
- Store screen for Support and future Premium content.
- Local profile/preferences for selected theme and skin.
- Stable IDs for future cosmetic/content items.
- A dedicated local profile layer for future billing entitlements.
- No ad SDK and no active purchase flow in the MVP.
- Figma screens for Store, Settings and the game-library shell.

## Local multiplayer
Two phones play over the same local network without internet during the match. TCP is used for the MVP.

## Build
GitHub Actions runs the JVM logic simulation and builds a debug APK on pushes to main. Release builds can be published from the same workflow and attach the APK directly to GitHub Releases.

The latest tested APK is published to Releases when a commit marked [release] passes the Android CI workflow.
