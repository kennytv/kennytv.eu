import type { Metadata } from 'next';
import PacketDataClient from './_components/PacketDataClient';

export const metadata: Metadata = {
  title: 'Minecraft Packet Data',
  description: 'Browse and compare Minecraft packet definitions across versions.',
};

export default function PacketDataPage() {
  return (
    <main className="min-h-screen">
      <div className="container py-6 md:py-8">
        <h1 className="mb-6 text-2xl font-bold text-text md:text-3xl">
          Minecraft Packet Data
        </h1>
        <PacketDataClient />
      </div>
    </main>
  );
}
