import { brandIcons } from '@/components/icons/BrandIcons';

interface SocialLinkProps {
  href: string;
  label: string;
  icon: keyof typeof brandIcons;
}

export default function SocialLink({ href, label, icon }: SocialLinkProps) {
  const Icon = brandIcons[icon];

  return (
    <li>
      <a
        rel="nofollow noopener"
        href={href}
        target="_blank"
        className="inline-flex items-center justify-center w-10 h-10 rounded-full
          text-text-dim hover:text-primary hover:scale-110
          bg-bg-card hover:bg-bg-card-hover border border-border hover:border-primary/40
          transition-all duration-300"
        aria-label={label}
      >
        <Icon className="w-4 h-4" />
      </a>
    </li>
  );
}
