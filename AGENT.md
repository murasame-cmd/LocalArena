# AGENT.md — LocalArena project handoff

## 1. Project identity

Project: **LocalArena**

Repository: `murasame-cmd/LocalArena`

Official repository:
https://github.com/murasame-cmd/LocalArena

Purpose:
An Android offline-first local multiplayer game platform. Two players on different phones should be able to play without internet, over the same local Wi-Fi/network.

Current games:
1. Chess — Шахматы
2. Sea Battle — Морской бой
3. Durak — Дурак

Current platform:
- Android
- Java
- TCP local networking
- minSdk 26
- compileSdk 36
- targetSdk 36
- namespace/applicationId: `com.localarena`

iOS is NOT implemented yet.

## 2. How to work with the user

The user is Russian-speaking and prefers:
- casual Russian;
- direct, practical communication;
- actual implementation instead of long tutorials;
- when a bug is found, fix it directly rather than waiting for approval;
- make a GitHub commit after meaningful fixes;
- run tests/build after changes;
- report only verified results.

Important:
- Do NOT claim that a physical two-phone test was performed unless the user actually provides the result/logs or a real device test environment exists.
- CI logic tests + APK build are NOT equivalent to real Wi-Fi multiplayer testing.
- If a test fails, investigate and fix it rather than hand-waving.

## 3. Product philosophy / monetization

The base edition is intentionally a complete free game.

Free base edition:
- no ads;
- no artificial gameplay limits;
- no energy/lives;
- no waiting timers;
- no crippled “demo” mechanics;
- local multiplayer does not depend on monetization.

Future optional monetization:
- 💖 Support the author — voluntary, any amount;
- ⭐ Premium — optional skins, themes, effects and other cosmetic/additional content.

Critical rule:
**Premium is NOT a “remove ads” purchase.**
The base version is already ad-free.

Current MVP:
- payments are NOT connected;
- billing is NOT connected;
- paid content is NOT active;
- no ad SDK;
- store UI may show future placeholders.

Do not reintroduce a `PREMIUM_NO_ADS` entitlement or `adsRemoved` logic unless product direction is explicitly changed.

## 4. Repository / release state

The project has GitHub Actions CI.

Workflow:
`.github/workflows/android.yml`

CI currently:
1. checkout;
2. JDK 17;
3. Gradle 9.6.1;
4. JVM logic tests;
5. debug APK build;
6. upload APK artifact;
7. for release commits, publish APK to GitHub Releases.

The project uses:
- AGP 9.3.3
- Gradle 9.6.1
- Java 17 in CI

Latest verified crash-fix release at the time of this handoff:
**v0.1.0-build-71**

Release:
https://github.com/murasame-cmd/LocalArena/releases/tag/v0.1.0-build-71

APK:
`LocalArena-v0.1.0-build-71.apk`

Latest verified CI run:
- workflow run: #71
- result: success
- purpose: crash-hardening build
- tests + APK build passed.

Older known successful build:
- run #45
- commit: `ce6bbb2cc106f61ef5cf85998817f5bd921fa30b`
- run ID: `36077517611`
- result: success

Do not assume build-71 is bug-free just because CI is green.

## 5. Figma design

Figma file:
**Local Games — Chess • Sea Battle • Durak**

file_key:
`qkMoHCeSm9dg0jThZWZh7y`

URL:
https://www.figma.com/design/qkMoHCeSm9dg0jThZWZh7y

The design contains screens for:
- splash / brand;
- main screen;
- create room;
- join room;
- waiting for player;
- connection errors;
- chess gameplay/result;
- sea battle setup/gameplay/result;
- Durak gameplay/attack/defense/result;
- game picker;
- settings;
- design system;
- reconnect;
- monetization/store;
- monetization/settings;
- game library/platform shell.

Visual direction:
- dark mobile UI;
- game-specific accents;
- reusable buttons/chips;
- compact touch-friendly layouts;
- game-specific boards/cards.

Important design/code decisions:
- Sea Battle MVP uses **automatic fleet placement**, not manual placement.
- Chess currently auto-promotes to queen; there is no promotion dialog.
- Some main-screen/game-picker/reconnect UX may still differ from the original Figma and needs QA.
- Store design follows the finalized free/no-ads + future Premium model.

## 6. Source structure

Main Java files:
- `app/src/main/java/com/localarena/MainActivity.java`
- `app/src/main/java/com/localarena/LocalNet.java`
- `app/src/main/java/com/localarena/ChessGame.java`
- `app/src/main/java/com/localarena/SeaBattleGame.java`
- `app/src/main/java/com/localarena/DurakGame.java`
- `app/src/main/java/com/localarena/MonetizationState.java`
- `app/src/main/java/com/localarena/MonetizationCatalog.java`

Tests:
- `app/src/test/java/com/localarena/LogicSimulationTest.java`

## 7. Networking model

The MVP uses TCP local networking.

Expected flow:
1. Host creates room.
2. Host sees local IP / port.
3. Guest enters host IP.
4. Guest connects.
5. Host selects game.
6. Both clients receive synchronized game state.
7. Game actions are sent over TCP.

The game state must NEVER leak hidden opponent information.

Sea Battle:
- host keeps full state;
- client receives masked opponent ships;
- `encodeFor(viewer)` masks opponent ship cells;
- `SEA_READY|player|grid` sends each player's own grid;
- incoming grids are validated.

Durak:
- viewer receives their own hand;
- opponent hand is masked as a count;
- deck is masked as a count;
- public attack/defense state remains visible.

Chess:
- client actions include player identity;
- host validates that client actions come from player 1;
- host validates whose turn it is.

## 8. Important security / validation rules already implemented

Do not remove these protections.

### Chess
- host validates client player ID;
- move accepted only when it is black's turn for the guest;
- king capture is prohibited by normal move generation;
- castling is restricted to the king's home square;
- en passant handling was fixed;
- chess state serialization is tested.

### Sea Battle
- action validates player ID;
- action validates turn;
- coordinates are bounds checked;
- duplicate shots are rejected;
- winner state stops further shooting;
- remote grids require exactly 100 cells;
- only 0/1 are accepted for incoming ready grids;
- fleet validity is checked;
- fleet must be:
  - 1×4
  - 2×3
  - 3×2
  - 4×1
- ships cannot touch;
- ships must be straight;
- hidden opponent ships are masked.

### Durak
- attack/defense actions are validated;
- attack count is capped according to current defender hand and 6-card maximum;
- taking with no uncovered attack cards is rejected;
- turn changes are controlled by game logic;
- both hands empty are handled as a draw;
- trump is displayed by suit;
- deck count is masked on clients.

### Network parser
Malformed network messages should be rejected safely:
- length/prefix checks;
- numeric parsing protection;
- invalid Sea Battle grid length rejected;
- exceptions must not crash the UI thread.

## 9. Known recent crash investigation

A physical test on a Xiaomi phone showed a system dialog:

“В приложении "LocalArena" снова произошел сбой”

The user reported the sequence:
- host creates room;
- after host chooses a game mode, the app crashes;
- guest phone then shows:
  **“Подключение: Connection reset”**
  and waits for connection.

Interpretation:
The guest's “Connection reset” is likely a consequence of the host-side failure/socket closing, not necessarily the root cause.

The exact Android stack trace was NOT obtained.

Do NOT claim the root cause was definitively proven.

The following hardening was implemented in build-71:
1. Sea Battle fleet placement was made more crash-resistant.
2. A deterministic valid fallback fleet was added.
3. Sea Battle placement is stress-tested across many seeds.
4. `startGame()` was guarded against runtime exceptions so unexpected initialization failures should return to a safe room/error state instead of immediately killing the app.
5. CI was run again and succeeded.
6. Release v0.1.0-build-71 was published.

Next task should be to reproduce the exact physical crash if it still happens.

## 10. Current Sea Battle implementation notes

Sea Battle is currently automatic-placement MVP.

`randomPlace()`:
- attempts randomized placement;
- validates fleet;
- has a deterministic fallback layout;
- should never fail just because random placement did not find a layout.

`validFleet()` validates:
- 10×10 board;
- valid components;
- max ship length 4;
- no bent ships;
- exact fleet composition;
- no touching ships, including diagonal touching.

If modifying this code:
- preserve hidden-state behavior;
- preserve serialization compatibility;
- rerun tests.

## 11. Current Durak implementation notes

Durak is a simplified MVP, not yet a complete formal Russian Durak rules engine.

Current mechanics include:
- 6-card hands;
- trump;
- attack;
- defense;
- take;
- pass/bito;
- refill;
- win/draw detection;
- attack limit up to 6 and limited by defender's hand size.

Still needs deeper audit for exact rules:
- attack-after-take;
- attacker/defender rotation;
- exact refill order;
- deck exhaustion;
- all legal rank attack restrictions;
- trump logic;
- final winner logic;
- post-defense turn ownership;
- edge cases around empty hands/deck.

Do not describe Durak as fully rules-complete yet.

## 12. Current Chess implementation notes

Chess engine has already received hardening:
- en passant bug fixed;
- castling home-square validation;
- king capture prohibited;
- serialization tests;
- e2-e4 smoke test;
- castling test;
- en passant test.

Known MVP limitation:
- pawn promotion currently auto-promotes to queen;
- no promotion selection UI.

This is acceptable as an MVP only if documented; otherwise implement a promotion dialog and update Figma.

## 13. UI / UX known issues

Potential follow-up areas:
- reconnect UX;
- connection error UX;
- waiting state;
- main screen/game picker parity with Figma;
- actual screen scaling on Xiaomi/Android;
- touch targets;
- back navigation;
- result screens;
- store/settings integration.

Back navigation has been hardened:
- Home → exit
- Room → close network and Home
- Picker → Room
- game → Picker
- other screens → Home

## 14. Testing protocol

Whenever making meaningful changes, follow this loop:

### A. Static/code audit
Inspect:
- MainActivity;
- LocalNet;
- all three game engines;
- serialization;
- network action validation;
- lifecycle/back navigation.

### B. Logic simulation
Run:
`gradle :app:testDebugUnitTest --stacktrace`

### C. APK build
Run:
`gradle :app:assembleDebug --stacktrace`

### D. CI
Push/commit and verify GitHub Actions:
- test step passes;
- APK build passes;
- artifact exists;
- if release commit, Release exists and contains APK.

### E. Physical two-phone test
Must be treated separately from CI.

For each game:
1. Host creates room.
2. Guest connects.
3. Host chooses game.
4. Both screens transition correctly.
5. Play a complete or representative game.
6. Test invalid actions.
7. Test back navigation.
8. Test disconnect/reconnect behavior.
9. Repeat with host/guest roles swapped where practical.

If a physical test fails:
- capture Android crash dialog;
- ideally get logcat/stack trace;
- identify exact failing method;
- fix;
- add a regression test;
- rebuild.

## 15. Critical distinction: CI vs real device testing

CI proves:
- Java logic tests pass;
- project compiles;
- APK can be packaged;
- release workflow works.

CI does NOT prove:
- two real Android phones can connect;
- Wi-Fi routing works on every device;
- Xiaomi/HyperOS lifecycle behavior is correct;
- socket handling survives real disconnects;
- UI is correct on all screen sizes;
- Android permissions/network settings behave correctly.

Always state this distinction.

## 16. Release workflow

The workflow is designed so commits whose message contains:
`[release]`

publish a GitHub Release with the generated APK.

A release should include:
- APK asset;
- concise changelog;
- build/test status.

The repository README should keep a direct link to:
https://github.com/murasame-cmd/LocalArena/releases/latest

## 17. Do not regress these product rules

Never:
- add ads to the base MVP;
- make Premium remove ads;
- add gameplay energy/lives/waiting timers;
- require payment to play;
- leak hidden opponent information;
- accept arbitrary client actions without validation;
- claim physical multiplayer testing was performed when it was not;
- claim a bug is fixed without running a relevant test/build.

## 18. Immediate next priorities

Recommended order:

### Priority 1 — reproduce the real Xiaomi crash
The most important open issue is the physical crash when the host chooses a game after a guest connects.

Get:
- exact reproduction;
- Android version/device;
- stack trace/logcat if possible.

Test all three:
- Chess;
- Sea Battle;
- Durak.

### Priority 2 — network lifecycle audit
Audit `LocalNet` for:
- socket closing;
- accept thread;
- reader thread;
- partial messages;
- duplicate connections;
- stale sockets;
- disconnect;
- reconnect;
- UI-thread safety;
- exceptions.

### Priority 3 — complete game-rule audit
Especially Durak.

### Priority 4 — UI/Figma audit
Compare actual screens against:
https://www.figma.com/design/qkMoHCeSm9dg0jThZWZh7y

### Priority 5 — improve release process
Keep GitHub Releases as the simple APK download path.

## 19. Useful links

Repository:
https://github.com/murasame-cmd/LocalArena

Releases:
https://github.com/murasame-cmd/LocalArena/releases

Latest release at handoff:
https://github.com/murasame-cmd/LocalArena/releases/tag/v0.1.0-build-71

Figma:
https://www.figma.com/design/qkMoHCeSm9dg0jThZWZh7y

## 20. Handoff instruction to the next agent

Start by reading this file and then inspect the current repository state.

Do NOT assume the historical summaries above are still perfectly synchronized with HEAD. Verify:
- current commit;
- current workflow;
- current releases;
- current source files;
- current tests.

Then continue from the highest-priority open issue:
**real two-phone multiplayer crash / connection-reset investigation.**

The user expects the agent to proactively fix discovered bugs, commit them, run tests, build an APK, and update the GitHub Release when appropriate.

When reporting progress, distinguish clearly between:
- verified by code/tests;
- verified by GitHub Actions;
- verified on a real device by the user.
