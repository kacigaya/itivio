import { Demo } from "./demo";
import { DownloadCard } from "./download";
import { REPO, latestDownloads } from "./release";

const base = process.env.BASE_PATH;
const repoUrl = `https://github.com/${REPO}`;
const fileUrl = (path: string) => `${repoUrl}/blob/main/${path}`;

const FEATURES = [
  {
    name: "Where to board",
    text: "On the Paris metro, the app shows the part of the train nearest to your transfer or exit, and the exit number.",
  },
  {
    name: "Live times",
    text: "Countdowns and delays where the transit agency publishes them. Scheduled times have no live marker.",
  },
  {
    name: "All modes",
    text: "Bus, tram, metro, train, and ferry. Leave now, leave at a time, or arrive by a time.",
  },
  {
    name: "Walking directions",
    text: "The next turn, the distance to it, and the arrival time. Your position stays on the device.",
  },
  {
    name: "Disruptions",
    text: "Service alerts for your saved stops and for the stops near you.",
  },
  {
    name: "Any screen",
    text: "Phones, tablets, and foldables. Light and dark themes with Material You colors. English and French.",
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
      <header className="bar">
        <a className="brand" href={`${base}/`}>
          <img src={`${base}/logo.svg`} alt="" width={40} height={40} />
          itivio
        </a>
        <a href={repoUrl}>Source code</a>
      </header>

      <main>
        <section className="hero" aria-labelledby="title">
          <div className="hero-text">
            <h1 id="title">Public transport for Android that keeps your data on your device.</h1>
            <p className="lead">
              Plan journeys by bus, tram, metro, train, and ferry. Open source, no account, no ads, no analytics.
            </p>

            <DownloadCard initial={d} />
          </div>

          <Demo src={`${base}/demo.mp4`} poster={`${base}/demo-poster.webp`} />
        </section>

        <section className="features" aria-labelledby="features-title">
          <h2 id="features-title">What it does</h2>
          <dl>
            {FEATURES.map((f) => (
              <div key={f.name}>
                <dt>{f.name}</dt>
                <dd>{f.text}</dd>
              </div>
            ))}
          </dl>
          <p className="aside">
            Routes come from <a href="https://transitous.org/sources/">Transitous</a>, a community service. Coverage
            differs by region, and some regions have no live data.
          </p>
        </section>

        <section className="privacy" aria-labelledby="privacy-title">
          <div className="privacy-inner">
            <h2 id="privacy-title">What leaves your phone</h2>
            <p>
              Favorites, recent searches, and settings stay on your device. The app does not log your journeys or your
              location. It sends requests to three services only:
            </p>
            <div className="table-wrap">
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
            <p>
              Read the <a href={fileUrl("PRIVACY.md")}>privacy policy</a> for location rounding, saved stops, and data
              deletion.
            </p>
          </div>
        </section>

        <section className="install" aria-labelledby="install-title">
          <h2 id="install-title">Install</h2>
          <ol>
            <li>
              <span>
                On your Android device, select <a href="#download">Download APK</a>.
              </span>
            </li>
            <li>
              <span>Open the file. If Android asks, allow installs from your browser or file manager.</span>
            </li>
            <li>
              <span>Open itivio. Location access is optional: you can search and plan without it.</span>
            </li>
          </ol>
        </section>
      </main>

      <footer>
        <p>
          itivio is free software under the <a href={fileUrl("LICENSE")}>GNU GPL, version 3 or later</a>.
        </p>
        <p className="links">
          <a href={repoUrl}>Source code</a>
          <a href={fileUrl("PRIVACY.md")}>Privacy policy</a>
          <a href={fileUrl("CHANGELOG.md")}>Changelog</a>
          <a href={`${repoUrl}/releases`}>All releases</a>
        </p>
        <p className="credits">
          Map data © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors. Typeface: Google
          Sans Flex, <a href={fileUrl("licenses/GoogleSansFlex-OFL.txt")}>SIL Open Font License 1.1</a>.
        </p>
      </footer>
    </>
  );
}
