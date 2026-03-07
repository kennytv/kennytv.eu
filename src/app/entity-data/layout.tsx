import EntityDataHeader from './_components/EntityDataHeader';

export default function EntityDataLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <EntityDataHeader />
      {children}
      <footer className="mt-8 border-t border-border bg-bg-surface">
        <div className="container py-4 text-center text-sm text-text-dim">
          Data parsed from decompiled Minecraft source
        </div>
      </footer>
    </>
  );
}
