# Kijkbuis frontend guide

How a frontend talks to the Kijkbuis backend. This covers the flow and the endpoints, not every field: the backend's
handlers document the details.

## Basics

- The frontend is served from the same origin as the API, so the session cookie is sent automatically. During
  development, proxy `/api` and `/live` to the backend (port 6742 by default), e.g. with Vite:
  ```js
  server: { proxy: { '/api': 'http://localhost:6742', '/live': 'http://localhost:6742' } }
  ```
- Requests and responses are JSON. Errors look like `{ "error": "A message you can show the user" }`, with a fitting
  HTTP status. A `401` anywhere means the user isn't logged in (anymore).
- Timestamps are ISO 8601 in UTC, such as `2026-10-26T20:00:00Z`.

## 1. Logging in

Logging in goes through SSO, and the backend handles the whole flow.

1. On startup, call `GET /api/me`.
   - `200` returns the user: `{ id, username, displayName, privileges, groups }`.
   - `401` means not logged in: send the browser to
     `/api/auth/login?redirect=<current path>`. After logging in, the user lands back on that path.
2. Use `privileges` to decide what to show:
   - `admin`: the admin screens
   - `transcode`: the quality picker
3. To log out, call `POST /api/auth/logout` (responds `204`). This only ends the Kijkbuis session, not the one at the
   identity provider. It also stops the streams this session started; the user's other devices keep theirs.

## 2. Channels and what's on

- `GET /api/channels` lists every channel the user can watch, in playlist order. Add `?playlist=<id>` for one
  playlist. Each channel has:
  `id, playlistId, name, group, logoUrl, channelNumber, tvgId, epgChannelId, now, next`
- `now` and `next` are programmes or `null`. A programme looks like
  `{ startsAt, endsAt, title, subtitle, description, category, iconUrl, posterUrl, backdropUrl }`.
- The images come straight from the EPG, any of them can be `null`. `posterUrl` is portrait, `backdropUrl` is
  landscape (a backdrop, or else a still from the programme), and `iconUrl` can be any shape. Load them with
  `referrerpolicy="no-referrer"`, since some image hosts refuse requests from other sites.
  Fetch the list again when `now` ends to keep it current.
- `GET /api/channels/<id>` returns a single channel, in the same shape.
- `GET /api/playlists` returns `{ id, name, channelCount }` per playlist, handy for grouping channels.

## 3. The guide

`GET /api/guide?from=<time>&to=<time>` returns the programmes in a time window:

```json
{ "from": "...", "to": "...", "channels": { "<channel id>": [ { "startsAt": "...", "title": "...", ... } ] } }
```

- The window defaults to the next 6 hours and can be at most 48 hours, so load more as the user scrolls.
- Optional filters: `playlist=<id>`, and `channel=<id>`, which may be repeated.
- Channels without programmes in the window are left out.

## 4. Watching a channel

### Start a stream

First find out which video codecs the browser can play:

```js
const candidates = { h264: 'avc1.640028', hevc: 'hvc1.1.6.L120.90', av1: 'av01.0.08M.08' };
const videoCodecs = Object.entries(candidates)
    .filter(([, codec]) => MediaSource.isTypeSupported(`video/mp4; codecs="${codec}"`))
    .map(([name]) => name);
```

Then start the stream:

```js
const response = await fetch('/api/streams', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channelId, quality: 'auto', videoCodecs }),
});
const stream = await response.json();
// { id, channelId, playlistUrl, quality, transcoding, sourceVideoCodec, sourceInterlaced, sourceWidth, sourceHeight }
```

The request only returns once the first segment is ready, which usually takes a few seconds. Show a loading state in
the meantime.

When `quality` is `auto`:
- Video the browser can play is passed through untouched.
- Anything else is transcoded to H.264, if the user has the `transcode` privilege.
- Interlaced video is transcoded to deinterlace it, since browsers show it with combing. Users without the `transcode`
  privilege get it passed through.
- `transcoding` in the response tells you whether it's transcoded.

### Play it

Play `stream.playlistUrl` (HLS with fMP4 segments) with [hls.js](https://github.com/video-dev/hls.js), or natively
where hls.js isn't supported:

```js
if (Hls.isSupported()) {
    const hls = new Hls();
    hls.loadSource(stream.playlistUrl);
    hls.attachMedia(video);
} else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = stream.playlistUrl;
}
```

The playlist URL contains a secret token and works without the session cookie. So you can also hand
`location.origin + stream.playlistUrl` to VLC, a Chromecast or AirPlay.

### Stop it

- Call `DELETE /api/streams/<id>` when the player closes or switches channel.
- When the page is closed, use `fetch(url, { method: 'DELETE', keepalive: true })`.
- Streams that nobody requests anything from are stopped automatically after the idle timeout (30 seconds by
  default). That can also happen to a long-paused player. If segments start returning `404`, start a new stream.

### When starting fails

| Status | Meaning                                                                                   |
|--------|-------------------------------------------------------------------------------------------|
| 403    | The user picked a quality without the `transcode` privilege                               |
| 404    | The channel doesn't exist                                                                  |
| 422    | The browser can't play the channel's codec, and the user may not transcode               |
| 502    | The channel couldn't be started, e.g. because the provider is down                        |
| 504    | The channel took too long to start                                                        |

The `error` message is fine to show as is.

### Picking a quality

- `GET /api/profiles` returns the qualities `{ id, name, width, height, bitrateKbps }`. Only show the picker to users
  with the `transcode` privilege.
- A profile is a 16:9 box: channels are scaled down to fit in it, keeping their aspect ratio, and never up. So only
  offer a profile when the channel reaches it: `sourceWidth >= width || sourceHeight >= height`. `sourceWidth` and
  `sourceHeight` are the size the channel is shown at, so channels with non-square pixels (720x576 shown as 16:9 is
  1024x576) and any aspect ratio work out. They're `null` if unknown.
- To switch quality:
  1. Start a new stream with `quality: '<profile id>'`.
  2. Switch the player over to it.
  3. Stop the old stream.

### Multiview

Each tile is simply its own stream: start, play and stop them independently, and mute all but one. For small tiles,
consider picking a lower quality if the user may transcode.

## 5. Admin screens

These require the `admin` privilege.

### Playlists

| Endpoint                             | Does                                                                    |
|--------------------------------------|-------------------------------------------------------------------------|
| `GET /api/playlists`                 | Lists playlists, with URLs and refresh status for admins                |
| `POST /api/playlists`                | Adds a playlist: `{ name, url, epgUrl?, refreshIntervalMinutes? }`      |
| `PATCH /api/playlists/<id>`          | Changes the fields you send. `"epgUrl": null` clears it                 |
| `DELETE /api/playlists/<id>`         | Removes a playlist and its channels                                     |
| `POST /api/playlists/<id>/refresh`   | Reloads the channels now, and then the EPG                              |

Things to know:
- Adding a playlist, changing its URL and refreshing it all respond once the channels are loaded. A problem with the
  URL shows up in `lastRefreshError`.
- The EPG then loads in the background. Poll the playlist while `epgRefreshing` is `true`, and show
  `epgLastRefreshError` if it's set.
- Scheduled refreshes only download the EPG if the EPG server says it changed. Refreshing by hand always downloads
  it again.
- `activeEpgUrl` is the EPG URL that's actually used: `epgUrl` if set, otherwise the one the playlist advertises
  (`detectedEpgUrl`).
- `refreshIntervalMinutes` defaults to 720 (12 hours), and can be anything from 5 to 10080.

### Fixing the EPG for a channel

When a channel shows no programmes, its tvg-id probably doesn't match the EPG:

1. `GET /api/playlists/<id>/epg-channels` lists the EPG's channels as `{ id, displayName, iconUrl }`. Let the admin
   pick one.
2. `PATCH /api/channels/<id>` with `{ "epgChannelId": "<picked id>" }` maps the channel to it. Use `null` to go back
   to the tvg-id. The EPG reloads in the background afterwards.

Admins also see `streamUrl` and `epgChannelOverride` on channels.

### Who's watching

- `GET /api/streams` lists every running stream, the longest running first. Each one is a stream as described above,
  plus `channelName`, `channelLogoUrl`, `startedAt` and `user` (`{ id, username, displayName, privileges, groups }`).
- Admins can stop anyone's stream with `DELETE /api/streams/<id>`. The viewer's player then gets `410 Gone` for its
  files (for 10 minutes), and should show the error instead of starting a new stream like it would after a `404`.

### Users

- `GET /api/users` lists everyone who ever logged in, most recent login first: `{ id, username, displayName,
  privileges, groups, createdAt, lastLoginAt, sessionCount }`.
- `DELETE /api/users/<id>/sessions` signs a user out everywhere and stops their streams, like stopping them one by one
  does. Their groups are read again
  when they log back in, so this applies group changes in the identity provider right away.
