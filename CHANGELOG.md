# Changelog

## 0.1.0 — Android MVP
- First public APK release for testing.
- Chess, Sea Battle and Durak are included.
- Offline multiplayer over the same local Wi-Fi network.
- Base edition is free, without ads or gameplay limits.
- Voluntary author support and future Premium cosmetics are planned, but payments are not connected yet.

## 0.1.0 — Release build
- Release automation syntax was hardened and verified for the APK publishing workflow.
- First automated Release publication is enabled for the tested APK.

- APK is now published directly as a GitHub Release asset.
- The repository's **Releases** page is the primary download location for published APK builds.
- GitHub Actions still keeps the APK as a workflow artifact for CI/debugging.

## Unreleased
- Defined the base edition as a complete free experience with no ads.
- Separated voluntary author support from future Premium content.
- Removed the future "remove ads" entitlement from the monetization catalog.
- Added stable placeholders for future cosmetic Premium content.
- Kept billing, payments, paid content and ads disabled until the base MVP is finished.
- Existing offline multiplayer games remain the core of the MVP.

## Release automation
- Current MVP release publishing is triggered by a commit containing `[release]` in its message.
