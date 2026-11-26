import type { ReactNode } from "react";
import { Demo } from "./demo";
import { DownloadCard } from "./download";
import { Reveal } from "./reveal";
import { REPO, latestDownloads } from "./release";

const base = process.env.BASE_PATH;
const repoUrl = `https://github.com/${REPO}`;
const fileUrl = (path: string) => `${repoUrl}/blob/main/${path}`;

const iconProps = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
} as const;

const FEATURES: { name: string; text: string; accent: string; icon: ReactNode }[] = [
  {
    name: "Where to board",
    text: "On the Paris metro, the app shows the part of the train nearest to your transfer or exit, and the exit number.",
    accent: "a1",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <rect x="5" y="3" width="14" height="14" rx="3.5" />
        <path d="M5 10.5h14" />
        <path d="M9 14h.01M15 14h.01" />
        <path d="M8.5 17 6 21M15.5 17 18 21" />
      </svg>
    ),
  },
  {
    name: "Live times",
    text: "Countdowns and delays where the transit agency publishes them. Scheduled times have no live marker.",
    accent: "a2",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <circle cx="12" cy="12" r="8.5" />
        <path d="M12 7.5V12l3.2 1.9" />
      </svg>
    ),
  },
  {
    name: "All modes",
    text: "Bus, tram, metro, train, and ferry. Leave now, leave at a time, or arrive by a time.",
    accent: "a3",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <circle cx="6" cy="6" r="2.6" />
        <circle cx="18" cy="18" r="2.6" />
        <path d="M8.6 6H15a3 3 0 0 1 3 3v6.4" />
      </svg>
    ),
  },
  {
    name: "Walking directions",
    text: "The next turn, the distance to it, and the arrival time. Your position stays on the device.",
    accent: "a2",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <path d="M5 20c7.5 0 5.5-9 14-9" />
        <path d="M15.6 7.6 19 11l-3.4 3.4" />
      </svg>
    ),
  },
  {
    name: "Disruptions",
    text: "Service alerts for your saved stops and for the stops near you.",
    accent: "a3",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <path d="M12 4 21 19.5H3Z" />
        <path d="M12 10v4" />
        <path d="M12 16.8h.01" />
      </svg>
    ),
  },
  {
    name: "Any screen",
    text: "Phones, tablets, and foldables. Light and dark themes with Material You colors. English and French.",
    accent: "a1",
    icon: (
      <svg {...iconProps} aria-hidden="true" focusable="false">
        <rect x="2.5" y="5" width="13" height="9.5" rx="2" />
        <rect x="15" y="9.5" width="6.5" height="9.5" rx="1.8" />
        <path d="M6 18.5h6" />
      </svg>
    ),
  },
];

const SERVICES = [
  {
    name: "Transitous",
    href: "https://transitous.org/",
    use: "Search, routes, departures, disruptions, live trip times",
    sent: "Search text, journey start and end points, journey options, the stops and trips you open",
  },
  {
    name: "IGN Géoplateforme",
    href: "https://cartes.gouv.fr/",
    use: "French address search",
    sent: "Search text, without your location",
  },
  {
    name: "OpenFreeMap",
    href: "https://openfreemap.org/",
    use: "Map tiles",
    sent: "The map area you look at",
  },
];

export default async function Home() {
  const d = await latestDownloads(process.env.GITHUB_TOKEN);
  return (
    <>
      <div className="amb-layer" aria-hidden="true">
        <div className="amb a" />
        <div className="amb b" />
        <div className="amb c" />
      </div>
      <Reveal />

      <header className="top">
        <div className="bar">
          <a className="brand" href={`${base}/`}>
            <img src={`${base}/logo.svg`} alt="" width={40} height={40} />
            itivio
          </a>
          <a className="nav-cta" href={repoUrl}>
            Source code
          </a>
        </div>
      </header>

      <main>
        <section className="hero" aria-labelledby="title">
          <div className="hero-text rv">
            <h1 id="title">
              Public transport for Android that <span className="hl">keeps your data on your device</span>.
            </h1>
            <p className="lead">
              Plan journeys by bus, tram, metro, train, and ferry. Open source, no account, no ads, no analytics.
            </p>

            <DownloadCard initial={d} />
          </div>

          <Demo src={`${base}/demo.mp4`} poster={`${base}/demo-poster.webp`} />
        </section>

        <section className="features" aria-labelledby="features-title">
          <div className="head rv">
            <h2 id="features-title">What it does</h2>
          </div>
          <dl className="grid">
            {FEATURES.map((f) => (
              <div key={f.name} className={`card rv pop ${f.accent}`}>
                <span className="ico" aria-hidden="true">
                  {f.icon}
                </span>
                <dt>{f.name}</dt>
                <dd>{f.text}</dd>
              </div>
            ))}
          </dl>
          <p className="aside rv">
            Routes come from <a href="https://transitous.org/sources/">Transitous</a>, a community service. Coverage
            differs by region, and some regions have no live data.
          </p>
        </section>

        <section className="privacy" aria-labelledby="privacy-title">
          <div className="privacy-inner">
            <div className="head rv">
              <h2 id="privacy-title">What leaves your phone</h2>
            </div>
            <p className="rv">
              Favorites, recent searches, and settings stay on your device. The app does not log your journeys or your
              location. It sends requests to three services only:
            </p>
            <div className="table-wrap rv pop">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Service</th>
                    <th scope="col">Use</th>
                    <th scope="col">Data sent</th>
                  </tr>
                </thead>
                <tbody>
                  {SERVICES.map((s) => (
                    <tr key={s.name}>
                      <th scope="row">
                        <a href={s.href}>{s.name}</a>
                      </th>
                      <td data-label="Use">{s.use}</td>
                      <td data-label="Data sent">{s.sent}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="rv">
              Read the <a href={fileUrl("PRIVACY.md")}>privacy policy</a> for location rounding, saved stops, and data
              deletion.
            </p>
          </div>
        </section>

        <section className="install" aria-labelledby="install-title">
          <div className="head rv">
            <h2 id="install-title">Install</h2>
          </div>
          <ol className="steps">
            <li className="rv pop">
              <span>
                On your Android device, select <a href="#download">Download APK</a>.
              </span>
            </li>
            <li className="rv pop">
              <span>Open the file. If Android asks, allow installs from your browser or file manager.</span>
            </li>
            <li className="rv pop">
              <span>Open itivio. Location access is optional: you can search and plan without it.</span>
            </li>
          </ol>
        </section>
      </main>

      <footer>
        <div className="cols">
          <div>
            <p>
              itivio is free software under the <a href={fileUrl("LICENSE")}>GNU GPL, version 3 or later</a>.
            </p>
            <p className="credits">
              Map data © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors. Typeface:
              Google Sans Flex, <a href={fileUrl("licenses/GoogleSansFlex-OFL.txt")}>SIL Open Font License 1.1</a>.
            </p>
          </div>
          <ul className="links">
            <li>
              <a href={repoUrl}>Source code</a>
            </li>
            <li>
              <a href={fileUrl("PRIVACY.md")}>Privacy policy</a>
            </li>
            <li>
              <a href={fileUrl("CHANGELOG.md")}>Changelog</a>
            </li>
            <li>
              <a href={`${repoUrl}/releases`}>All releases</a>
            </li>
          </ul>
        </div>
      </footer>
    </>
  );
}
