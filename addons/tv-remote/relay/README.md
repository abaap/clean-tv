# TV Remote relay

A Cloudflare Worker with one Durable Object per TV, so a phone can reach the TV from outside the home
network. The TV (`RemoteRelay.java` in the add-on) keeps a WebSocket open to `/tv`; the phone page at `/` (an
installable web app) posts commands to `/api/cmd`. You deploy it to your own Cloudflare account; the free plan
is enough for a household.

- **Pairing**: open TV Remote on the TV and scan the QR code. It holds `https://<relay>/#<TV id>.<phone key>`.
  The TV sends the relay only the SHA-256 of the phone key; the first connection for a TV id claims it (the TV
  key's hash is stored), later ones must use the same key. "New code" on the TV makes a new phone key, which
  unpairs every phone.
- **Commands** pass through the relay by name (`{cmd, text?, args?}`), so a new feature usually needs only
  `src/page.js`. Features: `banner`, `banner_clear`, `lock`, `unlock`, `lock_timer` (minutes),
  `lock_timer_cancel`, `set_pin` (4 digits), `screen_off`, `screen_on`, `standby`, `wake`, `set_psk`, `ping`.
  Building blocks: `sony` {service, method, params, version} (any IP control method), `button`
  {name | names[], gapMs} (any remote button, e.g. VolumeUp, Netflix, Num1; "A|B" tries A, then B), `buttons`,
  `intent` {action, data, type, package, component, categories, extras}, `launch` {package}, `apps`,
  `global` {action: back|home|recents|notifications|quick_settings|lock_screen}, `local` {method, path}
  (`/api/state` and `/api/report[?range=7d]`, Screen time), `type` {text, enter}, `adb` {action:
  status|on|off}, `info`, `caps`. The answer's `data` carries any result.
- **Live view**: a phone opening the live view makes the relay tell the TV `{t:"live", on, fps}`; the TV sends
  JPEG frames as binary messages, which the relay hands to the watching phones only.
- **Live status**: phones open `/phone?id=…` and send `{t:"auth", key}`; the relay pushes
  `{t:"status", online, power, state, name, lastSeen}` whenever something changes. A TV that drops off still
  counts as online for 15 s (a Wi-Fi blip reconnects within seconds); lastSeen is its latest keep-alive.
- **Notifications**: Web Push. The relay makes its own VAPID key pair the first time it needs one and keeps it in
  a Durable Object no request can reach; to bring your own instead, `node make-vapid.mjs` prints a pair to store as
  the Worker secrets `VAPID_PRIVATE` and `VAPID_PUBLIC`, which then win. "On" when the TV says hello, "off" on its screen-off
  event or after two minutes without a connection. On iPhone only from the Home Screen app. Set `VAPID_SUBJECT`
  in `wrangler.jsonc` to a `mailto:` address of yours if you like (push services may use it to reach you).
- **Power**: screen off/on, standby and wake use the TV's Sony IP control (Settings › Network & Internet ›
  Remote device settings › Control remotely: On, then Home network › IP control › Authentication: Normal and
  Pre-Shared Key); save the same key from the phone page. Without a key, standby falls back to the add-on's
  accessibility service. Wake from standby is best effort: the TV usually drops the connection in standby.
- **Stored**: TV key hash, phone key hashes, push subscriptions, TV name, power and lock state, last seen.
  Invocation logs are off in `wrangler.jsonc`, since the manifest URL carries the phone key.

## Deploy

```bash
cd addons/tv-remote/relay
npx wrangler login            # once, in a browser
npx wrangler deploy           # prints https://tv-remote.<your-subdomain>.workers.dev
# optional, for notifications:
node make-vapid.mjs
npx wrangler secret put VAPID_PRIVATE
npx wrangler secret put VAPID_PUBLIC
```

Then build the TV app with that address: `./gradlew assembleRelease -PrelayUrl=https://tv-remote.<your-subdomain>.workers.dev`
(see [`../README.md`](../README.md)). To use another Worker name, change `name` in `wrangler.jsonc`.

The phone page's icons are drawn by `python3 icons/make-icons.py`, which rewrites `src/icons.js`. The page's
font is Outfit, under the SIL Open Font License 1.1 ([`FONT-LICENSE-OFL.txt`](FONT-LICENSE-OFL.txt)), embedded in
`src/font.js`.

This relay comes from the launcher fork [github.com/ptrinh/tvhome-remote](https://github.com/ptrinh/tvhome-remote),
where the TV side is built into Home; the same relay and page serve both.
