// Makes the VAPID key pair for the relay's notifications (Web Push) and prints the two Worker secrets.
// Run once, from addons/tv-remote/relay:  node make-vapid.mjs
// Then:  npx wrangler secret put VAPID_PRIVATE   (paste the JSON line)
//        npx wrangler secret put VAPID_PUBLIC    (paste the key line)
// Keep a copy somewhere private: a new pair means every phone has to switch notifications on again.
import { webcrypto as crypto } from "node:crypto";

const b64url = (buf) => Buffer.from(buf).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
const pub = b64url(await crypto.subtle.exportKey("raw", pair.publicKey));
console.log("VAPID_PRIVATE:");
console.log(JSON.stringify({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y, d: jwk.d }));
console.log("VAPID_PUBLIC:");
console.log(pub);
