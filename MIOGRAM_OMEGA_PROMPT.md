# MIOGRAM OMEGA PROMPT — full-context brief for maximum improvement

> You are given ~5M tokens of context. Do NOT waste them re-exploring the
> repository: every essential fact about this codebase is already extracted
> below. Spend your tokens on IMPROVEMENT, not analysis.

---

## 1. MISSION

Miogram is a Telegram Android client fork. Your mission: **improve absolutely
everything, as much as possible** — product, architecture, performance,
battery, security, UI/UX coherence, code health, build speed, tests.

**You decide HOW.** No implementation instructions follow — only facts,
current state, and known problems. Choose the highest-leverage changes
yourself, in the order you judge best. Be bold but keep the app building
and working: every change must compile (`assembleRelease`) and must not
regress core Telegram behavior (connect, send/receive, calls, media).

When done, report a changelog grouped as `+ Added / − Removed / ~ Changed`.

---

## 2. IDENTITY & STACK (facts)

- Repo: `fuckramochka/miogram`, branch `main`. App: Miogram (Міограм).
- Base: Telegram Android + AyuGram/Nekogram/Nagram/exteraGram layers + custom
  `app.miogram.bridge` subsystem. Package: `com.exteraless.app` (legacy name,
  do not rename — store listing, providers, and user data depend on it).
- Java 21 + Kotlin (JVM_21), AGP 9.3.1, Gradle 9.7.1, compileSdk 37,
  buildTools 36.0.0, NDK 27.2.12479018, minSdk 27, targetSdk 36.
- Chaquopy Python 3.11 embedded runtime (pip: bs4/debugpy/lxml/packaging/
  pillow/requests/PyYAML). ABI: arm64-v8a + x86_64 single APK.
- Native: boringssl/exoplayer/ffmpeg/tgnet/voip; CMake
  `-DANDROID_PLATFORM=android-27 -DSTL=c++_static -DFLEXIBLE_PAGE_SIZES=ON`
  + ccache. WASM runtime NOT shipped (see §7).
- UI: programmatic Android Views (`LayoutHelper`, `AndroidUtilities.dp`,
  `Theme.getColor`), trilingual strings via `MiogramLocale.get(uk,ru,en)`.
  Resources: `res/values/strings.xml` + `strings_{na,nax,neko,oe,oe_*}.xml`
  × ~14 locales (en default, uk fully maintained).
- Backend: Supabase project `dbxsnjoeyiqvqtrluvwu` — tables
  `miogram_badges` (badge identity + presence in `client_version`),
  `miogram_users` (presence), `miogram_steam`. Full DDL + RLS + RPCs in
  `supabase_schema.sql` (idempotent, includes 2026-09-10 anti-abuse and
  2026-09-18 founder-only migrations).
- CI: `.github/workflows/release.yml` — push to main → arm64-v8a Release
  APK → GitHub pre-release (+ `[stable]` in msg = stable) → optional
  Telegram-channel post. `website.yml` only builds `miosite/` pages.
- Version: `TMessagesProj/build.gradle` (`verCode` hardcoded, name from run
  number), `gradle.properties` (`APP_VERSION_CODE/NAME`, `GLYPH_API_KEY=test`
  debug key). Secrets for Telegram API come from CI secrets, not the repo.

---

## 3. ARCHITECTURE MAP — custom layer

### 3.1 `TMessagesProj/src/main/java/app/miogram/bridge/` (the Miogram subsystem)

- `MiogramLocale.java` — trilingual helper, used everywhere in custom UI.
- `ai/` — `MiogramAiService` (Gemini text core, BYOK keyring round-robin);
  `companion/` — Ame-chan/KAngel companion chat (`Activity`, memory,
  persona prompts, `Toolbox` tool-call executor, prefs);
  `tools/MioTool.java` — vetted AI-tool registry on the `MioHook` bus.
- `badge/` — 10 canonical badge styles (`MiogramBadgeType`), pixel renderer
  (`MiogramArrowDrawable`), lore/grant bottom sheets, `MiogramBadgeManager`
  (`FOUNDER_USER_ID = 8011880648`), `MiogramSupabaseBridge` (REST+RPC
  client), opt-in dialog.
- `bypass/` — anti-censorship Fake-TLS engine + proxy pool UI.
- `cloudvault/` — AES-256-GCM vault in a forum supergroup (`#MVLT:Base64`
  manifests, >2GB chunking). No local index cache (zero-storage design).
- `customui/` — Custom Appearance studio + prefs + haptics + `MiogramUiEngine`
  canvas engine (bubbles, avatar rings/glow, name FX, dialog cards).
- `discord|github|roblox|spotify|steam/` — keyless public-API presence
  integrations (Lanyard, GitHub API, Roblox API+cookie, Spotify broadcast,
  Steam community XML) + sheets/cards + cloud sync via presence payload.
- `presence/` — `MiogramCloudPresence` (`#PRESENCE#` snapshot encoded in
  `client_version`), 90s refresher, connected-apps hub, per-user link
  ownership.
- `divine/` — one-click global preset orchestrator + Theme hot cache.
- `feed/` — Smart Feed timeline + AI digests. `folders/` — subfolder bar,
  engine, settings. `kanban/` — 4-column task board.
  `fun/` — `tg://musor_drop` / `tg://rules` easter eggs.
- `hooks/MioHook.java` — ordered vetoable hook bus with 3-strike disable.
- `localizer/` — live string-override browser + engine.
- `lyrics/` — LRC engine (LRCLib→NetEase→ID3→Genius cascade), karaoke view,
  source picker, floating ticker.
- `media/` — TikTok/YT/IG/X/Pinterest extractor (TikWM + Cobalt instances)
  + download pill + send flow.
- `multichat/` — split-screen chats + floating PiP chat service.
- `music/` — search across TG-cloud/Deezer/iTunes/Jamendo/Audius/YouTube.
- `perf/` + `performance/` — 120Hz unlock, FPS controller, trim-memory.
- `player/` — compact+fullscreen audio player, backdrops, theme editor,
  Apple-Music-style sheet, bass visualizer.
- `plugins/` — Plugin Forge (NL→plugin scaffolds; honest about no on-device
  Rust), hook manager, in-app notification banners, forge activity.
- `push/` — push-chain diagnostics sheet (FCM/Play/battery/keep-alive).
- `settings/` — 5-section main hub `MiogramSettingsActivity`.
- `ui/` — Chats/Privacy/Visuals/Performance/AI settings, glassmorphism
  + glass-effect engines, update sheet, physics interpolators;
  `ame/` NSO vaporwave aesthetic; `discord/` Discord-rail layout;
  `ios/` Cupertino port (large headers, tab bar, haptics, sounds, theme);
  `minimal/` slim rail; `player/` player sheets.
- `updater/` — GitHub-releases checker + resumable APK downloader +
  PackageInstaller receiver + progress bar + channel/promo dialogs.
- `userbot/` — Heroku-style userbot (`.ping/.help/.eval/.tr`, Bot helper,
  `.py` modules via Chaquopy `heroku_compat`, text filters, Lua shim).
- `vault/` — real/duress passcodes, decoy account, chat whitelist,
  panic logout (duress = UI-level filtering, NOT crypto isolation).
- `system/` — fake dialogs injector (Feed/Kanban pseudo-chats).

### 3.2 `TMessagesProj/src/main/java/app/exteraless/` (exteraGram ports)

`ai/` (LLM client/models/picker), `appearance/` (corners/FAB/M3/header/tabs
helpers + live previews), `backup/` (`.mio` JSON backup, zero-width
encryptor), `camera/` (CameraX + zoom sliders), `chats/` (double-tap cell,
sticker shape, wide posts), `components/` (QR, translate-before-send,
profile music, misc views), `crash/` (file log + report dialog),
`drawer/` (custom drawer, account picker with drag reorder, menu),
`feed/` (timeline/store/loader/channels), `glyph/` (Nothing Phone),
`icons/` (icon packs + picker + provider), `nowplaying/` (Last.fm),
`pillstack/` (telemetry pills: cpu/ram/net/DC-ping/weather/rates),
`plugins/` (62 files: Chaquopy engine, Xposed/LSPlant hook facade,
permissions/audit/sandbox gates, menus/intents/files bridges, full settings
UI), `proxy/` (disable conditions), `settings/` (Appearance/Chats/General/
Glyph/Other hub screens), `utils/` (markdown/media/back-anim/subtitles).

### 3.3 `TMessagesProj/src/main/kotlin/`

- `app/miogram/bridge/`: Gemini cloud client, on-device Whisper STT stack
  (backend iface, ONNX transcriber, BPE tokenizer, audio frontend), Android
  Keystore cipher, lock-screen gate + vault facade, SQLCipher adapters +
  history migrator, WAMR wasm runtime shim, blob store, decoy activity
  (**inert placeholder**), liquid-glass view, vault setup activity.
- `app/miogram/core/`: pure-JVM crypto (AES-GCM, Argon2id KDF, key wipe),
  vault models/codec, plugin trust (Ed25519 manifests, sandbox op-policy),
  STT contracts. Has unit tests — keep them green.
- `xyz.nextalone.nagram/NaConfig.kt`: central prefs incl. double-tap actions.
- `tw.nekomimi.nekogram`: Neko chassis — translator providers + LLM presets
  (`GOOGLE_AI_STUDIO` key shared with Miogram AI), Neko settings screens.

### 3.4 Python (`TMessagesProj/src/main/python/`, Chaquopy)

`base_plugin/hook_utils/client_utils/android_utils/file_utils/intents/
markdown_utils/pip_controller/plugin_settings/dev_server` bridges;
`elyx_runtime/` (structured `.elyx` plugins), `extera_utils/plugin_loader`
(99KB orchestrator: AST scan, perms, audit), `heroku_compat/module_runner`
(userbot `.py` executor, module cache), `ui/` (dialog/bulletin/settings DSL).

### 3.5 Bundled plugin + native shims

- `assets/plugins/Custom Profile.plugin` (+ repo-root copy): Python shim
  loading embedded zlib/base64 DEX (`cpb.CpbNative`) that installs LSPlant
  hooks for profile decor. **Gated OFF on SDK≥36** (Android 16 hook NPEs);
  works ≤35. Aliuhook 1.1.4 remains a dependency for the plugin engine.
- `jni/miogram/`: `host_api.fbs` + `miogram_wasm.c` (compiled only if
  `third_party/wasm-micro-runtime` exists — it doesn't).
- Repo-root `*.wasm` + `sdk/rust/miogram-plugin-sdk`: prebuilt/scaffold only.

---

## 4. UPSTREAM INTEGRATION POINTS (where custom code touches Telegram)

~108 files under `org/telegram/` reference custom code. Hot spots:
- `ApplicationLoader` (inits), `SendMessagesHelper` (pre-send veto, plugin
  hooks, userbot intercept), `MessagesController` + `ConnectionsManager`
  (update/request hooks), `NotificationsController` (duress filter, glyph,
  iOS sounds), `PasscodeView` (duress/decoy verdicts), `LocaleController`
  (string overrides), `MediaController` (HD/HDR/camera/glyph hooks),
  `MessageObject` (reaction gates, wide posts, zero-width strip).
- `Theme` (~35 Miogram branches: Discord/iOS palettes, custom bubbles,
  dividers, monet), `ActionBar*` (glass, haptics, iOS/Discord branches),
  `ChatMessageCell` (badges, bubbles, FX, haptics, summarize),
  `DialogCell` (badges, cards, glow+online dot, Discord/iOS branches),
  `ChatActivity` (~40 sites: menus, Kanban, split-chat, vault, AI, backup
  tap-routing, plugins), `DialogsActivity` (~35: subfolders, Discord rail,
  iOS header, duress filter, drawer items), `ProfileActivity` (~35: custom
  profile, badges, presence card, music), `SettingsActivity` (Miogram +
  Plugins entries), `LaunchActivity` (updater, plugins, decoy, crash).
- Manifest custom components: `MiogramDecoyActivity`,
  `MiogramVaultSetupActivity`, `MiogramSplitChatActivity`,
  `MiogramFloatingChatService`, `IconPackProvider`,
  `MiogramInstallReceiver` (+ 3 Neko-legacy entries).

---

## 5. KNOWN PROBLEMS (verified, not guesses)

1. **Hook fragility**: any LSPlant-hooked UI method can NPE on new Android
   versions (seen on API 36: `dispatchDraw`, `onCreateViewHolder` via the
   bundled DEX). The gate is per-plugin, not systemic.
2. **Swallowed exceptions**: ~200 `catch(Throwable ignored){}` in bridge
   (`ModernPlayerLayout` ~40 worst); real failures are invisible.
3. **Duress is UI filtering**, not crypto isolation (`cache4.db` open,
   search/push bypass). Don't market it as encryption.
4. **Presence rides `client_version`** in badge rows; users without
   founder-granted rows publish nothing. By founder's design, not a bug —
   but UX should say so.
5. **Secrets in repo**: Supabase URL + anon JWT (client + SQL) and
   `GRANT_SECRET` (client + `supabase_schema.sql`). Threat model = old
   builds/casual abuse, NOT reversers. If leaked broadly, rotate secret +
   rebuild + re-run SQL.
6. **Perf/battery**: 90s presence ticker; per-frame allocations in some
   draw paths (audit flagged `Theme.*Paint` mutation pattern — check).
7. **Prefs sprawl**: ~10 prefs files across forks; partial consolidation
   only. Duplicated settings surfaces (Mio hub vs extera screens) overlap.
8. **Phantom runtimes**: WASM/WAMR unshipped; on-device Rust forge absent
   (honestly stubbed — keep the honesty).
9. **Previous release was red** before the current green one; CI cache
   service is flaky (retries usually pass).

---

## 6. PRODUCT BACKLOG (founder's ideas, unordered — you prioritize)

- Folder/chat grid view (explorer-style) alongside list; subfolders polish.
- Cloud Vault redesign + real encryption settings UX.
- Playlist: true-random track, mood-fit track, built-in audio converter
  (mkv-as-file pain; ffmpeg exists in `jni/`).
- Badges: deeper Needy-Streamer art direction, premium-emoji interplay.
- Presence integrations depth (Steam/Spotify/Discord/GitHub/Roblox).
- Account UX, mention-notification edge cases, send-path robustness.

---

## 7. HARD GUARDRAILS (short list — everything else is your call)

- Keep `com.exteraless.app` package, providers, authorities, manifest names.
- Keep import compat: `.extera` + `.nekox-settings.json` +
  `.nekox-stickers.json` must keep importing (new files write `.mio*`).
- Keep `supabase_schema.sql` runnable/idempotent and in sync with
  `MiogramSupabaseBridge` ( anon SELECT + presence-column UPDATE only;
  identity via `miogram_grant_badge`/`miogram_revoke_badge` + secret).
- Founder id `8011880648` canonical in code + SQL + seed row.
- UI stays trilingual (`MiogramLocale.get(uk,ru,en)`), zero foreign-fork
  brand names in visible strings (14 locales done — keep it so).
- No XML layouts for core screens (programmatic Views convention);
  no allocations in hot `onDraw` paths; network off main thread.

---

## 8. RECENT HISTORY (do not revert blindly)

- v12.10.326 (green): detox (−4000 lines: modapi bridge, MiniChat,
  GhostKeeper, About/System hubs, XP theme, Steam watchlist, Discord DM
  rail, Lua command wiring, CloudVault backup export + disk persist,
  view_photo/find_music tools), full Mio rebrand (206 strings, 14 locales,
  @dkmiogram links, `.mio*` backups with legacy import), founder-only
  badges (RLS + RPC + secret), CPB gate on SDK≥36, Python module cache,
  single online dot, double-tap fallbacks.
- Known trade-offs accepted in that pass: Vault has no local index (slow
  on huge vaults); AI `find_chat` uses legacy fuzzy matcher (no exact
  phone/id fast-path); updater nags per-version; CloudVault chunk wait is
  60s single-shot; badge presence requires a founder-granted row.
- Commit before it (64e55fa55) was RED in CI; HEAD is green — don't assume
  older code compiled.

## 9. PROTOCOLS & CONVENTIONS CHEAT SHEET

- Presence: `MiogramCloudPresence.encodeClientVersion()` packs Steam/
  Discord/Spotify/GitHub/Roblox snapshot after a `#PRESENCE#` tag into
  `miogram_badges.client_version`; `extractPresence()` decodes it.
- Vault: AES-256-GCM file chunks + JSON manifests (`fileId,name,chunks,
  sha256`) posted as `#MVLT:Base64` messages in a dedicated forum
  supergroup; master key flows (`getMasterKeyHex`) — never log it.
- Userbot: `.py` modules declare commands/filters (`filter_outgoing`);
  Chaquopy executes via `heroku_compat/module_runner` (cached); Lua files
  are text-filter shims, not real Lua.
- Anti-block: Fake-TLS (`ee`-prefixed keys + SNI) + proxy pool from public
  GitHub raws; proxy-disable conditions per network.
- Tests live in `tests/` + `TMessagesProj/src/test` (12 files: STT/BPE,
  AES-GCM, KDF, vault codec, plugin trust/engine, history policy, AI
  routing). JVM-only, fast — run them before touching crypto/vault/AI.
- Commit style: `fix|feat(scope): ...`; releases auto-tag
  `v12.10.<run>` (beta) unless `[stable]` in message; `[skip publish]`
  skips the Telegram post.

## 10. DELIVERABLE FORMAT

- Code changes only (no plan-mode artifacts). Changelog as
  `+ Added / − Removed / ~ Changed`.
- Flag anything you intentionally did NOT do and why, plus anything needing
  the founder (Supabase dashboard runs, secret rotation, store metadata).

*Brief compiled 2026-09-18 from live tree (post v12.10.326 green build).
Trust it over older docs (`MI0GRAM_DEEP_AUDIT.md` 2026-09-02 is partly
stale; `CODEX_PROMPT.md` is a narrow player/UI-studio brief, still valid
for its 6 files).*
