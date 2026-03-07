import Footer from '@/components/layout/Footer';

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      {children}
      <Footer />
    </>
  );
}
