# AGENT.md — LocalArena current agent handoff

## 1. Project
- Repository: `murasame-cmd/LocalArena`
- Android Java app: `com.localarena`
- minSdk 26, compileSdk 36, targetSdk 36
- Games: Chess, Sea Battle, Durak
- Product: offline/local multiplayer over the same local network; no internet required for gameplay.
- Figma: `qkMoHCeSm9dg0jThZWZh7y`

## 2. User / working style
The user is Russian-speaking and wants direct implementation, not tutorials.
When a real bug is found:
1. inspect the actual current repository state;
2. fix it proactively;
3. add/adjust regression tests where possible;
4. run JVM tests;
5. build APK;
6. commit meaningful changes;
7. verify GitHub Actions;
8. report only what was actually verified.

Never claim a physical two-phone test happened unless the user provides the result/logs or an actual device test environment exists.

## 3. CRITICAL CURRENT ISSUE
Physical two-phone multiplayer is STILL NOT WORKING.

Latest user report:
- both phones still do not connect;
- on the host, after the connection screen, the host can reach the game-selection button/state;
- nevertheless the phones do not establish a usable multiplayer session;
- user explicitly says previous fixes did not change the real behavior.

This means the network problem is NOT considered solved.

Do not respond with another generic “build passed” as if this proves connectivity.

## 4. Current networking architecture
Main network class:
`app/src/main/java/com/localarena/LocalNet.java`

Current intended TCP flow:
1. Host binds TCP `0.0.0.0:47821`.
2. Host listens for a guest.
3. Guest connects to host IP on TCP 47821.
4. Handshake:
   - host sends `HELLO|1`
   - guest replies `HELLO_ACK|1`
5. Socket read timeout is reset after handshake.
6. Game messages then flow over the TCP connection.

Current discovery:
- UDP discovery port: `47822`
- request: `LOCALARENA_DISCOVER|1`
- response: `LOCALARENA_HOST|1|<ip>|47821`
- host starts a UDP responder;
- guest broadcasts discovery on available IPv4 interfaces and waits for a response.

Current diagnostics include:
- interface/IP logging;
- active-network / Wi-Fi capabilities;
- process binding attempts;
- discovery TX/RX/reply logging;
- TCP bind/listen/accept/connect logging;
- HELLO / HELLO_ACK logging;
- socket errors.

Current network thread names include:
- `ArenaDiscovery`
- host thread
- join thread
- discovery responder thread.

## 5. Android local-network permissions
Manifest currently includes:
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `NEARBY_WIFI_DEVICES`
- `ACCESS_LOCAL_NETWORK` (Android 17 / API 37)

Runtime requests were added for:
- `NEARBY_WIFI_DEVICES` on Android 13+
- `ACCESS_LOCAL_NETWORK` on Android 17+

Official Android documentation confirms that Android 16 local-network protections can affect raw TCP/UDP sockets, including outgoing/incoming TCP and UDP broadcast traffic. Android 17 requires `ACCESS_LOCAL_NETWORK` for apps targeting API 37+. Do not assume permissions alone solve this problem.

## 6. Wi-Fi network binding
`LocalNet` currently attempts to bind the process to an active Wi-Fi `Network` before:
- host start;
- join;
- discovery.

It restores the default process network on close.

This is a diagnostic/compatibility measure, NOT proof that LAN routing works.

## 7. Important current hypothesis
The previous implementation relied heavily on IPv4 UDP broadcast for discovery.

Possible failure points must now be distinguished instead of blindly adding more permissions:
1. Android local-network permission denied/restricted.
2. Guest broadcast leaves the phone but host never receives it.
3. Host receives discovery but guest never receives reply.
4. Discovery succeeds but TCP 47821 is blocked.
5. TCP connects but HELLO/HELLO_ACK fails.
6. TCP handshake succeeds but the game-session protocol fails when host selects a game.
7. Router/AP client isolation prevents phone-to-phone traffic.
8. Xiaomi/HyperOS or Android network lifecycle changes the active network/socket behavior.

A router with client/AP isolation cannot be fixed purely in Java TCP code. If isolation is confirmed, consider a Wi-Fi Direct/P2P fallback or another Android-supported local transport instead of endlessly changing TCP timeouts.

## 8. NEXT DEBUGGING RULE
Do NOT make another speculative network patch without first determining which stage fails.

The next agent should instrument or inspect the exact state transition and obtain evidence for:
- host TCP LISTEN;
- guest TCP CONNECT;
- host ACCEPT;
- HELLO sent;
- HELLO_ACK received;
- game-selection message sent;
- guest receives game-selection message;
- both sides enter the same game.

If the UI says the host can select a game, that alone does NOT prove the guest has a live synchronized socket.

Add a visible connection-state diagnostic if necessary, e.g.:
- DISCOVERY
- TCP_CONNECTING
- TCP_CONNECTED
- HANDSHAKE
- CONNECTED
- GAME_SYNC
- FAILED:<reason>

Prefer a deterministic “connection self-test” (UDP probe + TCP probe + handshake) over another long timeout.

## 9. Physical test protocol
The user must test on two real phones.

For a clean test:
1. Put both phones on the exact same Wi-Fi SSID.
2. Disable mobile data temporarily if possible.
3. Ensure Nearby devices / Local network permission is allowed.
4. On phone A choose Host.
5. On phone B choose Find Host or enter A's displayed IPv4 manually.
6. Capture what each phone shows.
7. If possible capture logcat filtered by:
   - `LocalNet`
   - `DISCOVERY_`
   - `TCP_`
   - `HELLO`
   - `LocalArenaCrash`

Interpretation:
- no DISCOVERY_RX on host -> discovery/network isolation issue;
- DISCOVERY_REPLY but no TCP_CONNECT -> guest routing or TCP block;
- TCP_CONNECT + no ACCEPT -> host/network issue;
- ACCEPT + no HELLO_ACK -> handshake/protocol issue;
- successful handshake + no GAME_SYNC -> application protocol bug;
- GAME_SYNC on host but not guest -> message delivery/parser/UI state bug.

## 10. CI vs physical testing
CI can verify:
- JVM tests;
- compilation;
- APK packaging;
- workflow/release.

CI CANNOT prove:
- two real phones can communicate over Wi-Fi;
- router/AP isolation;
- Xiaomi/HyperOS behavior;
- actual Android permission state;
- real socket routing;
- physical disconnect/reconnect.

Always distinguish these.

## 11. Current code areas to inspect
Priority:
1. `LocalNet.java`
2. `MainActivity.java`
3. all protocol send/receive paths
4. AndroidManifest
5. `LogicSimulationTest.java`
6. GitHub workflows

Potential UI issue:
- the app may transition the host into game selection even though guest synchronization is incomplete. Audit the exact message/event that causes this transition.

Also audit:
- partial TCP reads;
- newline/message framing;
- stale sockets;
- duplicate reader threads;
- socket close races;
- UI callbacks after network close;
- exceptions swallowed by empty catches;
- host/guest role state.

## 12. Game-state security rules
Preserve existing protections:
- Chess: validate player identity/turn; no king capture; serialization tests.
- Sea Battle: validate player/turn/coordinates/duplicate shots; validate 100-cell grids and fleet; mask hidden ships.
- Durak: validate attack/defense/take/pass/turn/card limits; mask opponent hand and deck.

Do not leak hidden opponent state.

## 13. Product rules
Base game is free and ad-free.
Do NOT add:
- ads;
- energy/lives;
- waiting timers;
- pay-to-play;
- Premium-as-ad-removal.

Future optional monetization:
- voluntary support;
- cosmetic/additional Premium content.

## 14. Release / CI
Current workflows:
- `.github/workflows/android.yml`
- `.github/workflows/release.yml`

Recent verified successful build:
- Android APK workflow #104
- Release workflow #45
- commit: `982c2f406648bd5052aff594085439c4ecc5f349`
- release tag: `v0.1.0-build-104`

This proves code/tests/build/release succeeded, NOT physical LAN multiplayer.

Before claiming a newer release, verify GitHub current state.

## 15. Known recent network commits
Relevant recent changes:
- `1a9845229dcf44fa1790c6a4c738073244e0bfe6` — cleartext local transport setting
- `b9f43cdda75f636c44769c48e212f3b476f733dd` — Nearby Wi-Fi permission
- `1b208f012261bf1d29e841d5bec8d2b50694560f` — nearby-devices permission
- `676410d527174664e5431215129e891d867d8b2a` — bind LAN sockets to Wi-Fi
- `de2881a05432dde3c3880fb88767db9c4355e134` — restore default network
- `38ceb4952cbaffdd7c9eafce370d8caa171217ed` — Android 17 local-network permission
- `129d2bc3b7decf81084b775f44b086da75e3238c` — Android 17 runtime request
- `982c2f406648bd5052aff594085439c4ecc5f349` — restore host address callback; build #104 passed

A previous build failure was caused by a missing `postHostAddress()` callback; this is fixed and build #104 passed.

## 16. Icon state
Launcher artwork was replaced with the supplied LocalArena artwork.
Important historical issue:
- an adaptive-icon XML accidentally referenced `@mipmap/ic_launcher` recursively;
- that XML was removed;
- legacy `mipmap-xxxhdpi/ic_launcher.webp` remains.

If the launcher still shows the Android robot on a real device, inspect the final APK resource table instead of assuming the icon is fixed.

## 17. Figma
Figma file:
`qkMoHCeSm9dg0jThZWZh7y`

No Figma visual update should be claimed unless a real Figma mutation was performed.

## 18. Required agent behavior
When opening a new chat:
1. read this AGENT.md;
2. verify HEAD/current branch;
3. inspect current `LocalNet.java` and `MainActivity.java`;
4. inspect latest CI/release;
5. reproduce/trace the connection state before patching;
6. fix the highest-confidence root cause;
7. commit every meaningful logical change;
8. run tests/build;
9. verify CI;
10. ask the user for the smallest useful physical test result/log if a real-device step remains.

The immediate goal is NOT “another green build”.
The immediate goal is:
**prove exactly where the two-phone connection breaks, then fix that stage.**
