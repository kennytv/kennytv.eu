import {
  Send,
  Link as LinkIcon,
  Plane,
  Settings,
  Database,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

const iconMap: Record<string, LucideIcon> = {
  send: Send,
  link: LinkIcon,
  plane: Plane,
  settings: Settings,
  database: Database,
};

interface ProjectCardProps {
  title: string;
  description: string;
  href: string;
  icon: string;
  delay?: number;
  small?: boolean;
}

export default function ProjectCard({
  title,
  description,
  href,
  icon,
  delay = 0,
  small = false,
}: ProjectCardProps) {
  const Icon = iconMap[icon] ?? Send;
  const Heading = small ? 'h4' : 'h3';
  const headingClass = small
    ? 'text-lg font-semibold text-text pb-1'
    : 'text-xl font-semibold text-text pb-1';

  return (
    <div
      className="opacity-0 animate-fade-in-up"
      style={{ animationDelay: `${delay}ms`, animationFillMode: 'forwards' }}
    >
      <div className="card p-5 group">
        <div className="flex gap-4 items-start">
          <a
            href={href}
            target="_blank"
            rel="noopener"
            className="flex-shrink-0 w-12 h-12 rounded-lg flex items-center justify-center
              text-primary bg-primary/10
              group-hover:bg-primary/15
              transition-colors duration-300"
            aria-label={title}
          >
            <Icon className="w-5 h-5" />
          </a>
          <div>
            <Heading className={headingClass}>
              <a
                href={href}
                target="_blank"
                rel="noopener"
                className="no-underline text-inherit hover:text-primary transition-colors duration-200"
              >
                {title}
              </a>
            </Heading>
            <p
              className="text-text-muted text-sm leading-relaxed relative z-10"
              dangerouslySetInnerHTML={{ __html: description }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
