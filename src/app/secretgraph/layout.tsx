import SecretgraphHeader from './_components/SecretgraphHeader';

export default function SecretgraphLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <SecretgraphHeader />
      {children}
      <footer className="mt-8 border-t border-border bg-bg-surface">
        <div className="container py-4 text-center text-sm text-text-dim">
          Data collected via bStats
        </div>
      </footer>
    </>
  );
}
