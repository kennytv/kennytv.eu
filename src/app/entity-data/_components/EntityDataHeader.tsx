import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { GitHubIcon } from '@/components/icons/BrandIcons';

export default function EntityDataHeader() {
  return (
    <header className="border-b border-border bg-bg-surface">
      <div className="container flex items-center justify-between py-4">
        <Link
          href="/"
          className="flex items-center gap-2 text-primary hover:text-primary-light transition-colors"
        >
          <ArrowLeft className="h-5 w-5" />
          <span className="hidden sm:inline">Back to kennytv.eu</span>
          <span className="sm:hidden">Back</span>
        </Link>
        <a
          href="https://github.com/kennytv/kennytv.eu"
          target="_blank"
          rel="noopener"
          className="text-text-dim hover:text-text transition-colors"
          aria-label="View source on GitHub"
        >
          <GitHubIcon className="h-6 w-6" />
        </a>
      </div>
    </header>
  );
}
