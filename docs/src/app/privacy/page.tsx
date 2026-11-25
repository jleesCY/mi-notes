"use client";

import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";

const UPDATED = "November 24, 2026";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-xl sm:text-2xl font-bold tracking-tight">{title}</h2>
      <div className="space-y-3 text-base leading-relaxed text-on-surface-variant">{children}</div>
    </section>
  );
}

export default function Privacy() {
  return (
    <main className="min-h-screen bg-surface-container-low text-on-surface transition-colors duration-300 overflow-x-hidden">
      <Navbar />
      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <article className="max-w-3xl mx-auto space-y-12">
          <header className="space-y-4">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter">
              Privacy <span className="text-primary italic">Policy</span>
            </h1>
            <p className="text-sm text-on-surface-variant font-medium">Last updated {UPDATED}</p>
            <p className="text-lg leading-relaxed text-on-surface-variant">
              mi. Notes collects no personal data. There are no accounts, no analytics, no ads and no crash reporters.
              Everything you write stays on your device.
            </p>
          </header>

          <Section title="What stays on your device">
            <p>
              Everything you write: your notes, folders, pictures and settings. They are stored in the app&apos;s private
              storage on your phone and nowhere else. You can delete them at any time with Settings → Backup &amp; Reset →
              Wipe All Data, or by uninstalling mi. Notes.
            </p>
            <p>
              They leave your phone only when you choose to: sharing a note, exporting a backup or Markdown files, or
              copying text. Exports are saved only where you choose to save them.
            </p>
          </Section>

          <Section title="Connections mi. makes">
            <p>One, to <strong>GitHub</strong> (api.github.com):</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>
                To check whether a newer version of mi. Notes is available, at most every 12 hours, and when you tap Check
                for Updates in Settings → About. It sends nothing about you or your notes; GitHub sees an ordinary request
                for the latest release.
              </li>
              <li>To download the new version, if you choose Install.</li>
              <li>After an update, to fetch that version&apos;s release notes for What&apos;s New.</li>
            </ul>
            <p>
              GitHub receives your IP address and basic device information, as with any web request, under its own
              privacy policy.
            </p>
          </Section>

          <Section title="Permissions">
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Internet</strong>: only for the update check, the update download and the release notes above.</li>
              <li><strong>Install apps</strong>: asked by Android the first time you install an update from inside mi. Notes.</li>
            </ul>
          </Section>

          <Section title="Children">
            <p>mi. Notes collects nothing from anyone, children included.</p>
          </Section>

          <Section title="Changes and contact">
            <p>
              If this policy changes, the new version will be posted on this page with a new date. Questions? Open an
              issue on <a className="text-primary hover:underline" href="https://github.com/jleescy/mi-notes/issues" target="_blank" rel="noopener noreferrer">GitHub</a>.
            </p>
          </Section>
        </article>
      </div>
      <Footer />
    </main>
  );
}
