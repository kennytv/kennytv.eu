import type { Metadata } from 'next';
import ConverterClient from './ConverterClient';

export const metadata: Metadata = {
  title: 'Minecraft Command Converter',
  openGraph: {
    title: 'Minecraft Command Converter',
    description: '1.20.4 → 1.20.5 command converter',
    url: 'https://kennytv.eu/converter',
  },
};

export default function ConverterPage() {
  return <ConverterClient />;
}
