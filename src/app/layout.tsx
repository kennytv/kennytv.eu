import type { Metadata } from 'next';
import { Raleway } from 'next/font/google';

import './globals.css';

const raleway = Raleway({
  subsets: ['latin'],
  weight: ['300', '400', '600', '700', '800'],
  variable: '--font-raleway',
  display: 'swap',
});

export const metadata: Metadata = {
  title: {
    default: 'kennytv',
    template: '%s | kennytv',
  },
  description:
    'kennytv personal website, a software developer specializing in Minecraft-related software like PaperMC, ViaVersion, and Hangar.',
  keywords: 'kennytv, Nassim Jahnke, Software Developer, Minecraft Plugins, PaperMC, Paper, ViaVersion',
  authors: [{ name: 'kennytv' }],
  metadataBase: new URL('https://kennytv.eu'),
  icons: {
    icon: [
      { url: '/assets/ico/favicon.ico' },
      { url: '/assets/ico/favicon-32.png', sizes: '32x32', type: 'image/png' },
      { url: '/assets/ico/favicon-192.png', sizes: '192x192', type: 'image/png' },
    ],
    apple: [{ url: '/assets/ico/apple-touch-icon.png', sizes: '180x180' }],
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={raleway.variable}>
      <body className="font-sans">
        {children}
      </body>
    </html>
  );
}
