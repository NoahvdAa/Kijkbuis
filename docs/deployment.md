# Running Kijkbuis with Docker

The image builds the frontend and the backend, and runs the backend with ffmpeg. The backend serves the frontend
itself, so there's one port to expose: 6742. The config and the database live in `/data`.

## Build

From the root of the repository:

```sh
docker build -t kijkbuis .
```

## First start

Kijkbuis runs as UID 1000, so give it a data directory it can write to:

```sh
mkdir data && sudo chown 1000:1000 data
docker run --rm -v ./data:/data kijkbuis
```

This writes `data/kijkbuis.toml` and stops. Fill in at least:

- `[server] public-url`: the URL people reach Kijkbuis on, such as `https://tv.example.com`. Register
  `<public-url>/api/auth/callback` as the redirect URI with your identity provider.
- `[auth]`: the `issuer`, `client-id` and `client-secret`, and which groups get which privileges. To let everyone
  change the quality without being in a group, use `transcode = ["*"]`. Admin can only be given to groups.

Leave `frontend-dir` empty: the image serves its own frontend.

## Run

```sh
docker run -d --name kijkbuis --restart unless-stopped -p 6742:6742 -v ./data:/data kijkbuis
```

Or with Compose:

```yaml
services:
  kijkbuis:
    build: .
    restart: unless-stopped
    ports:
        - "6742:6742"
    volumes:
        - ./data:/data
```

## Things to know

- Put a reverse proxy with HTTPS in front. Login cookies are only marked Secure when `public-url` starts with
  `https://`. Starting a channel can take up to the startup timeout (20 seconds by default), so don't let the proxy
  time out sooner.
- Transcoding (lower qualities, unsupported codecs, interlaced channels) runs libx264 on the CPU, once per viewer.
- Outside Docker, build the frontend with `npm run build` and point `server.frontend-dir` at `frontend/dist`.
- Plugins go in `data/plugins`, which Kijkbuis creates on its first start. Restart it to load new ones, see
  [plugins.md](plugins.md). Each plugin keeps its data in a directory named after it next to the jars.
- Kijkbuis currently does NOT re-use connections to a source, nor does it provide a way to limit the number of
  concurrent viewers for a playlist. If you have a source that can only handle a few connections, consider putting a
  proxy in front of it that does connection pooling, like [Dispatcharr](https://github.com/dispatcharr/dispatcharr).
