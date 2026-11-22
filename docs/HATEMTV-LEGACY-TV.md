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

This version provides addon channel browsing and playback. Legacy's EPG timeline, M3U/XMLTV and
Xtream import are not included. Descriptions are displayed as returned by the catalog; they are not
presented as verified current TV programme schedules. Playback still depends on an installed addon
returning a working stream. Hardware remote navigation and streams must be validated on a Mi Box.

CI tests source detection, raw types, pagination, source cancellation, addon disablement and retry,
along with the existing home focus/discovery tests. It also compiles Android UI regression tests and
builds a universal APK. The debug signing key is cached for subsequent CI builds; switching from older
uncached builds may require reinstalling. For durable distribution, supply a private release keystore.
