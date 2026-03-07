import type { Metadata } from 'next';
import HeroSection from '@/components/home/HeroSection';
import ProjectCard from '@/components/home/ProjectCard';
import { PROJECTS, SMALLER_PROJECTS } from '@/lib/constants';

export const metadata: Metadata = {
  openGraph: {
    title: 'kennytv - Software Developer',
    description: 'Explore my projects, like PaperMC, ViaVersion, and Hangar.',
    url: 'https://kennytv.eu',
  },
};

export default function HomePage() {
  return (
    <main>
      <HeroSection />

      {/* Projects section */}
      <section className="gradient-border-top py-16 md:py-24">
        <div className="container">
          <h2 className="mb-8 text-center text-2xl font-bold text-text md:text-3xl">
            NOTABLE PROJECTS
          </h2>

          {/* Main projects */}
          <div className="grid gap-4 md:grid-cols-3 md:gap-5">
            {PROJECTS.map((project, i) => (
              <ProjectCard
                key={project.title}
                {...project}
                delay={300 + i * 100}
              />
            ))}
          </div>

          {/* Smaller projects */}
          <div className="mx-auto mt-5 grid max-w-4xl gap-4 md:grid-cols-2 md:gap-5">
            {SMALLER_PROJECTS.map((project, i) => (
              <ProjectCard
                key={project.title}
                {...project}
                delay={600 + i * 100}
                small
              />
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
