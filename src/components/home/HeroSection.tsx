export default function HeroSection() {
  return (
    <section
      className="relative bg-cover bg-center bg-no-repeat py-24 md:py-36 text-center overflow-hidden"
      style={{ backgroundImage: "url('/assets/images/home-bg.webp')" }}
    >
      {/* Dark overlay */}
      <div className="absolute inset-0 bg-bg/40" />

      <div className="container relative z-10">
        <div
          className="mx-auto max-w-2xl opacity-0 animate-fade-in"
          style={{ animationDelay: '0.3s', animationFillMode: 'forwards' }}
        >
          {/* Main card */}
          <div className="hero-card p-8 mb-4">
            <h1
              className="inline-block border-2 border-primary py-5 px-8 mt-4 mb-8 text-3xl md:text-4xl font-bold tracking-widest bg-clip-text text-transparent"
              style={{ backgroundImage: 'linear-gradient(135deg, var(--color-primary), var(--color-primary-light))' }}
            >
              kennytv
            </h1>
            <h2 className="text-2xl md:text-3xl font-bold text-text pb-3">
              Software Developer
            </h2>
            <p className="text-text-muted">And clearly not a web designer.</p>
          </div>

          {/* Sponsors card */}
          <div className="hero-card overflow-hidden p-5 !bg-[#0d1117]">
            <iframe
              src="https://github.com/sponsors/kennytv/card"
              title="Sponsor kennytv"
              className="w-full h-[65px] border-0"
              style={{ colorScheme: 'dark', background: 'var(--color-bg-card)' }}
              loading="lazy"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
