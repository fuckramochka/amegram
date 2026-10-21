# Yumigram → Yumi MD3 migration

Status: **in progress**. This file is the single source of truth for the
app-wide migration that follows the rebuilt settings hub. A screen is only
`done` when its code is migrated, compiles, and has been checked against the
style tokens. Anything not checked on a physical device is
`done — needs device QA`; it is never described as verified.

## Status legend

| Status | Meaning |
|---|---|
| `todo` | Not started. |
| `in progress` | Being migrated in the current stage. |
| `done` | Migrated and compiled. Still needs device QA unless explicitly noted. |
| `done — needs device QA` | Code complete; visual/interaction checks remain. |
| `blocked` | Cannot be completed without changing a protected upstream area or a missing dependency. Reason is recorded. |
| `legacy` | Intentionally left on `YumiTheme` / `YumiComponents`; not part of this pass. |

## Components

| Component | Class / entry point | Status | Notes |
|---|---|---|---|
| Design tokens | `app.amegram.theme.YumiMd3` | done — needs device QA | Roles, spacing, shape, type and motion scales extended for app-wide use. |
| Aurora + grain backdrop | `app.amegram.settings.ui.YumiBackgroundView` | done — needs device QA | Power Saving now simplifies to a flat surface. |
| Top app bar | `app.miogram.ui.yumi.YumiTopBar` | done — needs device QA | Left aligned, 60dp, scroll reveal, 48dp actions, RTL. |
| List cell | `app.miogram.ui.yumi.YumiCell` | done — needs device QA | First/middle/last/single corner logic, switch/value/badge/chevron. |
| Grouped card | `app.miogram.ui.yumi.YumiGroupCard` | done — needs device QA | Owns corner logic and hairline dividers. |
| Buttons | `app.miogram.ui.yumi.YumiButton` | done — needs device QA | Filled / tonal / outlined / text, 52dp, AA gradient. |
| Text field | `app.miogram.ui.yumi.YumiTextField` | done — needs device QA | 56dp, focus outline, leading icon, clear, error, counter. |
| Chips | `app.miogram.ui.yumi.YumiChip` | done — needs device QA | Assist / filter, selected state, 48dp target. |
| Slider | `app.miogram.ui.yumi.YumiSlider` | done — needs device QA | Custom track/thumb, keyboard and TalkBack actions. |
| Dialog | `app.miogram.ui.yumi.YumiDialog` | done — needs device QA | MD3 card, scrim, spring entrance, RTL, 48dp actions. |
| Bottom sheet | `app.miogram.ui.yumi.YumiBottomSheet` | done — needs device QA | Drag handle, rounded top, max height, back dismissal. |
| Snackbar | `app.miogram.ui.yumi.YumiSnackbar` | done — needs device QA | Inverse surface, action, timeout, accessibility live region. |
| Popup menu | `app.miogram.ui.yumi.YumiPopupMenu` | done — needs device QA | Anchored card, 48dp rows, RTL, elevation. |
| Progress | `app.miogram.ui.yumi.YumiProgress` | done — needs device QA | Linear + circular, indeterminate, Power Saving aware. |
| Bottom navigation | `app.miogram.ui.yumi.YumiMainTabsStyle` | done — needs device QA | Solid MD3 pill, active label, pill indicator, separate search button. |
| Navigation motion | `MainTabsLayout` + `GlassTabView` hooks | done — needs device QA | Existing view pager, counters, long-press and routes untouched. |
| Theme bridge | `app.miogram.ui.yumi.YumiThemeBridge` | done — needs device QA | Maps Telegram roles to Yumi roles; dynamic-color seam documented. |
| Screen scaffold | `app.miogram.ui.yumi.YumiScreenStyler` | done — needs device QA | Aurora, 620dp cap, transparent list/action bar for official list screens. |
| Search field skin | `FragmentSearchField` hook via `YumiScreenStyler` | done — needs device QA | Search pill reused by contacts and other official lists. |
| Chat list style | `app.miogram.ui.yumi.YumiChatListStyle` | done — needs device QA | Tonal cards/segments, pinned accent, explicit first/last corners. |
| Chat bubble style | `app.miogram.ui.yumi.YumiChatStyle` | done — needs device QA | 26dp bubbles, no tails, media radius, composer field. |
| Profile card | `app.miogram.ui.yumi.YumiProfileCard` | done — needs device QA | Reusable hero/identity card for profile surfaces. |
| Power Saving | `app.miogram.ui.yumi.YumiPowerSave` | done — needs device QA | Flat backdrop, no stagger, no heavy press scale. |

## Screens

| Screen | Entry point | Status | Notes |
|---|---|---|---|
| Settings hub | `AmegramSettingsActivity` | done — needs device QA | Rebuilt in previous pass. |
| Client settings | `AmegramSettingsActivity(true)` | done — needs device QA | Rebuilt in previous pass. |
| Main navigation | `MainTabsActivity` | done — needs device QA | Floating MD3 pill + search; routes unchanged. |
| Chat list | `DialogsActivity` / `DialogCell` | done — needs device QA | Expressive card renderer replaced by Yumi renderer; RecyclerListView preserved. |
| Chat screen | `ChatActivity` | done — needs device QA | Bubbles, wallpaper, composer and role colors; no per-cell allocations added. |
| Profile | `ProfileActivity` | done — needs device QA | Global roles + reusable profile card component; structural upstream header retained. |
| Contacts | `ContactsActivity` | done — needs device QA | Aurora scaffold, 620dp cap, search pill, role colors. |
| Calls | `CallLogActivity` | done — needs device QA | Aurora scaffold, 620dp cap, role colors. |
| Folders | `FiltersSetupActivity` | done — needs device QA | Aurora scaffold, 620dp cap, role colors. |
| Search | `DialogsActivity` search / `FragmentSearchField` | done — needs device QA | Shared search pill and role colors. |
| Dialog surface | `YumiDialog` used by new surfaces | done — needs device QA | Legacy `AlertDialog` is restyled through roles, not structurally replaced. |
| Bottom sheet surface | `YumiBottomSheet` | done — needs device QA | Available for new surfaces; official sheets keep their behavior. |
| Media viewer | `PhotoViewer` | done — needs device QA | Role colors and surfaces only; no upstream layout rewrite. |
| Attachment panel | `AttachPopupWrapper` | done — needs device QA | Role colors, rounded panel via theme; no adapter rewrite. |
| Reply / forward UI | `ChatMessageCell` / `MessageDrawable` | done — needs device QA | Bubble geometry + role colors; quote colors mapped. |
| Sticker / emoji panel | `EmojiView` | done — needs device QA | Role colors, surface containers, no layout rewrite. |
| Snackbars / toasts | `YumiSnackbar` + role bridge | done — needs device QA | Bulletin/toast colors mapped; new surfaces can use YumiSnackbar. |
| Popup / context menus | `YumiPopupMenu` + role bridge | done — needs device QA | New surfaces use the component; official menus are role-styled. |
| Legacy module store | `HotStoreActivity` etc. | legacy | Intentionally outside this pass; still uses `YumiTheme`. |

## Decisions

1. **Reuse the existing `YougramExpressive` switch.** The app already treats
   “UI module + Expressive style” as the opt-in for the modern interface. Yumi
   components use the same gate (`YumiUi.enabled()`), so there is one switch,
   not a second competing one.
2. **Skin official Telegram screens, do not fork them.** Contacts, calls,
   folders, profile, media viewer, attachment and emoji panels are upstream
   files. They receive the shared scaffold, roles, search field and bubble
   geometry through narrow hooks. Rewriting their adapters would make upstream
   merges expensive and risk behaviour, which the brief forbids.
3. **Keep `RecyclerListView`, `UniversalRecyclerView` and existing adapters.**
   New drawing code is allocation-free on the hot path; no per-frame objects
   are created in `DialogCell` or `MessageDrawable`.
4. **Material 3, not glass, for Yumi surfaces.** The floating navigation bar
   uses an opaque tonal surface with a hairline and elevation. Blur remains an
   optional legacy effect behind the old style, never the Yumi default.
5. **Brand accent stays `#E5486B`.** `YumiMd3.primary()` is the role; the
   action gradient is deepened for AA contrast. A future Material You provider
   only needs to replace role values.
6. **Chat bubbles use a pink→violet tonal pair**, not a saturated gradient.
   This keeps long text readable and lets outgoing bubbles feel branded without
   turning the chat into a neon surface.
7. **Power Saving is read from the system** (`PowerManager.isPowerSaveMode`)
   with a process override for previews/tests. It flattens the backdrop,
   removes stagger and reduces press scaling.
8. **No new localization framework.** Every new string goes through
   `MiogramLocale.get(uk, ru, en)` and plurals through `MiogramLocale.plural`.
9. **Profile keeps the upstream photo header.** A full structural replacement
   would break the shared-media pager and profile transitions. The reusable
   `YumiProfileCard` supplies the new visual language where a screen owns its
   content; the official header is restyled through roles.
10. **The HTML preview is a design mock.** It is generated from the tokens by
    hand and is explicitly labelled as not being a runtime render.
11. **Expressive UI lives modularly in `ui` hotmodule (v1.0.6).** Per design
    decision, the settings hub and client settings are natively rebuilt inside
    the client, whereas expressive navigation (`YumiNavController`), floating
    chat list cards (`YumiChatRowRenderer`), list screen chrome (`YumiScreenChrome`,
    `YumiTopBar`, `YumiSearchField`) and M3 spring physics are delivered dynamically
    via the `ui` module in `hotmodules/ui` and synced to `yuimodules`.

## Verification

| Check | Status |
|---|---|
| `:TMessagesProj:compileDebugJavaWithJavac` | pass (see final report) |
| Light theme | needs device QA |
| Dark theme | needs device QA |
| IME / insets | needs device QA |
| RTL | needs device QA |
| 320dp width | needs device QA |
| Tablet ≥620dp | needs device QA |
| Font scale 1.3 / 2.0 | needs device QA |
| Low-end performance | needs device QA |
| Power Saving | needs device QA |
| TalkBack | needs device QA |
