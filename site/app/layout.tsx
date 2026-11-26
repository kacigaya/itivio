import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";

const sans = localFont({
  src: "./fonts/google-sans-flex.woff2",
  weight: "300 800",
  display: "swap",
  variable: "--font-sans",
});

const description =
  "A public transport app for Android that keeps your data on your device. Open source, no account, no ads.";

export const metadata: Metadata = {
  title: "itivio: public transport for Android",
  description,
  icons: { icon: `${process.env.BASE_PATH}/logo.svg` },
  openGraph: { title: "itivio", description, type: "website" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F5FBF7" },
    { media: "(prefers-color-scheme: dark)", color: "#0E1513" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={sans.variable}>
      <body>
        <script dangerouslySetInnerHTML={{ __html: `document.documentElement.classList.add("js")` }} />
        {children}
      </body>
    </html>
  );
}
