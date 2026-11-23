# Legacy TV integration

HatemTV adapts the remote-first sidebar and fullscreen artwork of
[Nuvio Native Legacy](https://github.com/iqui27/nuvio-native-legacy) in its existing Kotlin/Compose UI.
The reference client uses C/SDL2; this is an adaptation, not its native engine embedded in the APK.
The Legacy skin is enabled by default for HatemTV. Settings > Layout > Home layout >
Nuvio Legacy TV skin switches back to the Cinema top navigation. This preference is saved per profile.
The existing Modern/Classic/Grid home layouts and catalog settings still apply.

The sidebar exposes Films, Series, Live TV, Addons, Search, Library and Settings.
It scrolls when necessary so its last items remain reachable on 720p displays and with large text.
Back opens the sidebar; Right returns to content. The existing server trust dialog stays pinned and accessible.

Live TV discovers enabled addon catalogs whose raw type is tv, channel, channels, live or iptv,
following the types accepted by Legacy's src/guia.c. Search-only catalogs are excluded. Sources
are distinguished by URL as well as catalog identity, even when two installations share a manifest ID.
Selecting a source fetches its catalog, and More channels uses advertised skip pagination with deduplication.
Selecting a channel opens the existing stream selection and player directly without requiring a meta endpoint.
Exact manifest content types are preserved for addon requests. Stream headers and addon authentication
continue to use HatemTV's existing addon and player engines. Addon installations and enablement are shared
with Home and refreshed when they change. No third-party addon or credential is installed automatically.

The initial legacy-tv.1 build provided addon channel browsing and playback. EPG was added in
legacy-tv.2 below. M3U playlists and Xtream import are not included. Descriptions are displayed as returned by the catalog; they are not
presented as verified current TV programme schedules. Playback still depends on an installed addon
returning a working stream. Hardware remote navigation and streams must be validated on a Mi Box.

CI tests source detection, raw types, pagination, source cancellation, addon disablement and retry,
along with the existing home focus/discovery tests. It also compiles Android UI regression tests and
builds a universal APK. These CI APKs use debug signing keys. From legacy-tv.2 the workflow creates an explicit key file
and reuses it through an Actions cache (best effort; caches can expire). Updating from older builds
may require reinstalling after backing up settings. For durable distribution, supply a private release keystore.

## Version legacy-tv.2

Live TV now has List, Mosaic and Guide views with a focused-channel artwork/programme header,
white focus pills, time ruler, now indicator and a three-hour guide window. The Details banner is
collapsed by default on compact TV layouts so multiple channel rows fit on a Mi Box. Details can
show or hide the artwork/programme panel; larger layouts show it initially. Earlier/Now/Later
browse the schedule (up to six hours back and forty-eight hours ahead). The view is saved per profile.
OK opens stream selection; hold OK or select Channel options to organize a channel.

Folders can be created, renamed and deleted from Folders. A channel can belong to several folders.
Channel references include provider URL, exact content type and ID, avoiding collisions between
providers. Folders store references and artwork, never expiring playback URLs. Disabling an addon
hides its channels from folder playback, while retaining membership for later reactivation.
Folders, view choice, XMLTV source and manual EPG mappings are stored locally per profile.
They persist across app restarts; they are not synced to a server.

EPG accepts XMLTV and XMLTV.gz URLs; France/Italy presets use the same epgshare01 feeds referenced
by Legacy's src/epg.c. No EPG network requests happen until a source is configured and channels exist.
Sources are cached for twelve hours. A failed refresh retains the previous cache and labels it as stale.
The streaming parser retains schedules for the displayed/saved channels with bounded input sizes,
retained text/events and a 48-hour future window. Matching prefers exact channel IDs, then unique
normalized display names. Channel options allow an explicit XMLTV ID when automatic matching fails.
Unmatched channels show Programme unavailable; catalog descriptions are not used as fake EPG data.
A source may not cover every channel. The artwork panel is not a live video preview.

Validation covers profile isolation and persisted library serialization, folder membership across
providers, XMLTV times/timezones, matching ambiguity, guide geometry, gzip/cache and failed refresh.
UI regression tests are compiled in CI; actual Mi Box remote/playback validation remains necessary.

## Version legacy-tv.3 — channel player OSD

The integrated channel player adapts Legacy's src/aovivo.h/c layout: channel logo/name,
LIVE badge, actual source addon, current and next XMLTV programmes, programme progress and clock,
plus measured video/audio format labels. Info opens the existing detailed stream information panel.
Pause, Guide, Audio, Subtitles, Sources, Reload and Picture reuse existing player commands.
Guide stops playback and returns to the Live TV page; this version does not add Legacy's PiP guide
or channel zapping. The programme progress bar is informational, not a seek bar for a live stream.

A hidden OSD is revealed by OK, arrow keys, Info or Menu. The revealing press, held repeats and
release are consumed so revealing the panel does not pause or seek. Once visible, left/right
navigate the action buttons and OK activates one. Info/Menu can open or close technical info.
Back closes the information panel/controls before exiting. Controls reuse the three-second idle
hide timer while playing. Hardware media/volume keys and open dialogs retain their existing roles.

The originating channel's stable identity travels through stream selection into player navigation,
including saved-state restoration. It is used to resolve the same per-profile XMLTV mapping and
folder labels as Live TV. The source addon label follows the selected playback source. Unknown
EPG or decoder measurements are left unavailable; advertised stream titles do not become measured
quality badges. Films/series keep the standard player controls. External player apps supply their
own UI and are not affected by this in-app OSD.

Tests cover navigation context round trips, VOD/live selection, wake-key ownership, programme
selection/progress, measured format labels and profile-safe EPG mappings. Actual remote navigation
and decoder behaviour still need a Mi Box test. The CI signing cache is reused from legacy-tv.2.

## Version legacy-tv.4 — ordered EPG sources

EPG settings now store up to eight distinct URLs in priority order per profile. The existing single
URL is retained as the first source when opening an older library. Save commits the whole list;
Cancel leaves stored settings unchanged. France and Italy add presets to the list rather than
replacing it. Select a source to edit its URL; Move up/down changes priority and Delete removes it.
Add/Apply accepts a draft URL, then Save persists the list. Refresh uses the saved list.

Each channel uses the first source with at least one current or future programme. Missing,
ambiguous, empty or expired matches fall through to the next source. Entire schedules remain
separate: programmes from competing publishers and colliding XMLTV IDs are never merged.
An explicit channel ID is honoured in sources containing that ID; other sources try automatic
matching. This same policy is used by the guide, channel cards and integrated player.

Sources load sequentially and later sources are skipped once all requested channels have coverage.
A failed source does not prevent later sources from loading; partial failures are shown in Live TV.
A valid cached schedule remains eligible during a failed refresh. Raw source caches are separate,
limited to eight files, and retain the existing twelve-hour freshness policy. Parsed memory cache
is bounded to four target-specific entries. No recurring background download is added.

Tests cover priority changes, empty/expired/future schedules, cross-source ID collisions, manual
mapping, old JSON migration, ordered persistence/profile isolation, independent gzip/disk caching,
HTTP failure fallback and shared player use. UI regression tests are compiled in CI; Mi Box
remote validation remains necessary. Signing reuses the same cache as legacy-tv.3.

## Version legacy-tv.5 — right-side categories and movable folders

The channel categories and personal folders now occupy a scrollable column to the right of the
channel list, mosaic or guide. The active category has a persistent dot/background distinct from
remote focus. OK selects its channels. In list/guide, Right enters the category column; Left
returns to the last focused channel (or first available channel). The main toolbar stays above.

Hold OK on a category or folder to start moving it. Up/Down changes its draft position, keeping
focus on that same item and scrolling when required. Release of the initiating long press does
not confirm a move or select another category. A new OK press confirms the order; Back cancels.
Horizontal navigation is held inside the menu while moving. The New folder action stays last.
The order includes addon categories, so personal folders can be positioned between countries.
It is persisted locally per profile and survives reopening and folder renaming. Missing addon
categories are hidden while retaining their saved order; new entries appear at the end.

Unit tests cover ordering boundaries, draft immutability, category removal/addition, JSON
migration and persistence/profile isolation. A Compose regression covers move/confirm/cancel
without changing the selected category; it is compiled in CI, with Mi Box remote testing pending.
Version and signing remain compatible with the previous personal APK.


## Version legacy-tv.6 — categories on the left

The category/folder column is now on the left of the channel list, mosaic and guide. In list/guide,
Left enters the menu; Right returns from the menu to the last focused/first available channel.
Long OK, Up/Down, fresh OK confirmation and Back cancellation keep their existing behaviour.
The saved category order and all EPG/folder settings are preserved. Brand mockups are proposals
only; this navigation correction does not apply an unselected visual identity.

## Version legacy-tv.7 — Hatem Multi Media cinema identity

The personal full APK uses the selected red ribbon H/play identity and exact name Hatem Multi
Media. Native vector marks, launcher icon, TV banner and outlined wordmark remain sharp across
TV densities. All existing launcher components use the same identity, including users with a
previously selected alternate icon. Android system launch and the in-app startup share the mark.

The in-app startup has a dark red cinema backdrop, centered identity and three subtle animated
loading dots governed by the existing startup state. Profile selection uses the same backdrop,
a compact upper-left wordmark, rounded-square avatars and a white focus outline. Custom avatar
images and custom profile backgrounds remain supported. Illustrated defaults cover absent/failed
avatar images. Selection, PIN protection, editing, profile limits and focus handlers are reused.

Both in-app startup and profile pages carry the exact text "Application codé par El Habib Hatem"
in the physical top-right corner, at 9sp with 38% white opacity and TV-safe insets. Brand casing is
Hatem Multi Media throughout these screens. The left-side Live TV menu and all saved settings from
legacy-tv.6 remain included. Compile checks cover the branding/credit UI regression; Mi Box visual
and remote validation remains pending. Package identity and signing are retained for an update.
