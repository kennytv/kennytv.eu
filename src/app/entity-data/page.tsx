import type { Metadata } from 'next';
import EntityDataClient from './_components/EntityDataClient';

export const metadata: Metadata = {
  title: 'Minecraft Entity Data',
  description: 'Browse and compare Minecraft entity data fields across versions.',
};

export default function EntityDataPage() {
  return (
    <main className="min-h-screen">
      <div className="container py-6 md:py-8">
        <h1 className="mb-6 text-2xl font-bold text-text md:text-3xl">
          Minecraft Entity Data
        </h1>
        <EntityDataClient />
      </div>
    </main>
  );
}
