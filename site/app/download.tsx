"use client";

import { useEffect, useState } from "react";
import { latestDownloads, type Apk, type Downloads } from "./release";

const ABI_LABELS: Record<string, string> = {
  "arm64-v8a": "Most phones and tablets",
  "armeabi-v7a": "Older 32-bit phones",
  x86_64: "Emulators and some Chromebooks",
  x86: "Older emulators",
};

function Size({ bytes }: { bytes: number }) {
  return (
    <span className="amount">
      <b>{(bytes / 1e6).toFixed(1)}</b> MB
    </span>
  );
}

function DownloadIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 -960 960 960" width="24" height="24" fill="currentColor">
      <path d="M480-337q-8 0-15-2.5t-13-8.5L308-492q-12-12-11.5-28t11.5-28q12-12 28.5-12.5T365-549l75 75v-286q0-17 11.5-28.5T480-800q17 0 28.5 11.5T520-760v286l75-75q12-12 28.5-11.5T652-548q11 12 11.5 28T652-492L508-348q-6 6-13 8.5t-15 2.5ZM240-160q-33 0-56.5-23.5T160-240v-80q0-17 11.5-28.5T200-360q17 0 28.5 11.5T240-320v80h480v-80q0-17 11.5-28.5T760-360q17 0 28.5 11.5T800-320v80q0 33-23.5 56.5T720-160H240Z" />
    </svg>
  );
}

function AbiRow({ apk }: { apk: Apk }) {
  return (
    <li>
      <a href={apk.url}>{apk.abi}</a>
      <span>{ABI_LABELS[apk.abi] ?? ""}</span>
      <Size bytes={apk.size} />
    </li>
  );
}

export function DownloadCard({ initial }: { initial: Downloads }) {
  const [d, setD] = useState(initial);

  useEffect(() => {
    latestDownloads().then(setD, () => {});
  }, []);

  const version = d.tag.replace(/^v/, "");
  const date = d.publishedAt
    ? new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(d.publishedAt))
    : null;

  return (
    <div className="download" id="download">
      <p className="release">
        <b>itivio {version}</b>
        <span>
          {d.prerelease ? "Prerelease" : "Release"}
          {date && <> · {date}</>}
        </span>
      </p>
      <a className="button block" href={d.universal.url}>
        <DownloadIcon />
        Download APK
      </a>
      <p className="meta">
        <Size bytes={d.universal.size} /> · Android 8.0 or later · Works on all devices
      </p>
      {d.signed && (
        <p className="note">
          Versions up to 0.13.1 used a debug signing key. To update from them, uninstall the app first, which deletes your
          saved places.
        </p>
      )}
      <details>
        <summary>Smaller APK for your CPU type</summary>
        <ul className="abis">
          {d.perAbi.map((apk) => (
            <AbiRow key={apk.abi} apk={apk} />
          ))}
        </ul>
      </details>
      <p className="links">
        <a href={d.notesUrl}>Release notes</a>
        {d.checksumsUrl && <a href={d.checksumsUrl}>SHA-256 checksums</a>}
      </p>
    </div>
  );
}
