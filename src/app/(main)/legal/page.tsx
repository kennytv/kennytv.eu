import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Legal Notice',
};

export default function LegalNoticePage() {
  return (
    <main className="py-16 md:py-24">
      <div className="container max-w-3xl">
        <h1 className="mb-8 text-3xl font-bold text-text">Legal Notice</h1>

        <div className="text-sm leading-relaxed">
          <p>
            Nassim Jahnke
            <br />
            c/o flexdienst – #20549
            <br />
            Kurt-Schumacher-Straße 76
            <br />
            67663 Kaiserslautern
            <br />
            Deutschland
            <br />
            <br />
            Email: contact(at)njahnke.dev
          </p>
        </div>
      </div>
    </main>
  );
}
