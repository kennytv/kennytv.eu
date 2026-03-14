import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Privacy Policy',
};

export default function PrivacyPolicyPage() {
  return (
    <main className="py-16 md:py-24">
      <div className="container max-w-3xl">
        <h1 className="mb-8 text-3xl font-bold text-text">Privacy Policy</h1>

        <div className="space-y-8 text-sm leading-relaxed">
          <section>
            <h2 className="mb-2 text-lg font-semibold text-text">1. Controller</h2>
            <p>
              Nassim Jahnke
              <br />
              Email: contact(at)njahnke.dev
            </p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-text">2. Hosting</h2>
            <p>
              This website is hosted by an external service provider. When you visit this website,
              the hosting provider automatically collects information in server log files transmitted
              by your browser, including your IP address, browser type, operating system, and the
              page visited. This data is collected solely to ensure reliable operation of the
              website.
            </p>
            <p className="mt-2">Legal basis: Legitimate interest.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-text">3. Embedded Content</h2>
            <p>
              The homepage embeds an iframe from GitHub (github.com). When loading this page, a
              connection to servers of GitHub Inc. (USA) is established, and your IP address is
              transmitted to GitHub. For more information, see the{' '}
              <a
                href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement"
                target="_blank"
                rel="noopener noreferrer"
              >
                GitHub Privacy Statement
              </a>
              .
            </p>
            <p className="mt-2">Legal basis: Legitimate interest.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-text">4. Your Rights</h2>
            <p>
              You have the right to access, rectification, erasure, restriction, and objection
              (Art. 15–21 GDPR) as well as the right to lodge a complaint with a supervisory
              authority (Art. 77 GDPR).
            </p>
          </section>
        </div>
      </div>
    </main>
  );
}
