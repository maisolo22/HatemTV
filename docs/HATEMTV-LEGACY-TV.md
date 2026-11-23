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
