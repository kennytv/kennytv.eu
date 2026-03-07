import SocialLink from '@/components/ui/SocialLink';
import { SOCIAL_LINKS } from '@/lib/constants';

export default function Footer() {
  return (
    <footer className="gradient-border-top py-10 text-center text-text-dim">
      <div className="container">
        <p className="mb-4 text-sm">
          You can contact me via Discord (if you can find me).
          <br />
          For business inquiries: contact(at)njahnke.dev
        </p>
        <hr className="mx-auto mb-4 w-24 border-border" />
        <ul className="flex items-center justify-center gap-3">
          {SOCIAL_LINKS.map((link) => (
            <SocialLink
              key={link.label}
              href={link.href}
              label={link.label}
              icon={link.icon}
            />
          ))}
        </ul>
      </div>
    </footer>
  );
}
