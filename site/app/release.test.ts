import assert from "node:assert/strict";
import { test } from "node:test";
import { pickDownloads, type Asset, type Release } from "./release.ts";

const asset = (name: string): Asset => ({ name, size: 1, browser_download_url: `https://example.org/${name}` });
const release = (tag: string, names: string[], draft = false): Release => ({
  tag_name: tag,
  html_url: `https://example.org/${tag}`,
  published_at: "2026-11-18T18:06:41Z",
  draft,
  prerelease: true,
  assets: names.map(asset),
});

test("picks the newest published release and splits the APKs", () => {
  const d = pickDownloads([
    release("v0.13.0", ["itivio-0.13.0-universal-debug.apk"], true),
    release("v0.12.0", [
      "itivio-0.12.0-arm64-v8a-debug.apk",
      "itivio-0.12.0-universal-debug.apk",
      "itivio-0.12.0-x86_64-debug.apk",
      "SHA256SUMS.txt",
      "demo.mp4",
    ]),
  ]);
  assert.equal(d.tag, "v0.12.0");
  assert.equal(d.universal.url, "https://example.org/itivio-0.12.0-universal-debug.apk");
  assert.deepEqual(d.perAbi.map((a) => a.abi), ["arm64-v8a", "x86_64"]);
  assert.equal(d.checksumsUrl, "https://example.org/SHA256SUMS.txt");
});

test("accepts signed APK names without a build type suffix", () => {
  const d = pickDownloads([release("v1.0.0", ["itivio-1.0.0-universal.apk", "itivio-1.0.0-armeabi-v7a.apk"])]);
  assert.equal(d.universal.abi, "universal");
  assert.deepEqual(d.perAbi.map((a) => a.abi), ["armeabi-v7a"]);
  assert.equal(d.checksumsUrl, null);
});

test("fails the build when no universal APK exists", () => {
  assert.throws(() => pickDownloads([release("v0.12.0", ["itivio-0.12.0-arm64-v8a-debug.apk"])]), /no universal APK/);
  assert.throws(() => pickDownloads([]), /No published release/);
});
