import type { Metadata } from 'next';
import SecretgraphClient from './_components/SecretgraphClient';

export const metadata: Metadata = {
  title: 'Server Usage Statistics',
  description: 'Track Minecraft server software usage statistics over time.',
};

export default function SecretgraphPage() {
  return (
    <main className="min-h-screen">
      <div className="container py-6 md:py-8">
        <SecretgraphClient />
      </div>
    </main>
  );
}
