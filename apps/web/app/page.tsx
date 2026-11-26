const cells = [
  {
    title: 'One token source',
    body: 'tokens.json compiles to CSS variables and Compose objects, so web and Android cannot drift.',
  },
  {
    title: 'Client only',
    body: 'Static export on Firebase Hosting. No server rendering, no Cloud Functions, no billing plan.',
  },
  {
    title: 'Rules first',
    body: 'Firestore rules are the only access control, and they are tested in the emulator.',
  },
] as const

const swatches = [
  { name: 'Indigo', className: 'bg-tag-indigo' },
  { name: 'Sky', className: 'bg-tag-sky' },
  { name: 'Violet', className: 'bg-tag-violet' },
  { name: 'Amber', className: 'bg-tag-amber' },
  { name: 'Rose', className: 'bg-tag-rose' },
  { name: 'Emerald', className: 'bg-tag-emerald' },
] as const

export default function Page() {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-3xl flex-col justify-center gap-16 px-6 py-20">
      <header className="space-y-4">
        <p className="text-xs font-medium uppercase tracking-[0.18em] text-ink-muted">
          Slot · Phase 0
        </p>
        <h1 className="text-display font-semibold tracking-tight text-ink">A quieter schedule.</h1>
        <p className="max-w-prose text-md leading-relaxed text-ink-secondary">
          Scaffolding is in place: one design-token source feeding both clients, a static web
          output, and the Firestore rules harness. There is nothing to sign in to yet.
        </p>
      </header>

      <section className="grid gap-px overflow-hidden rounded-card border border-border bg-border sm:grid-cols-3">
        {cells.map((cell) => (
          <div key={cell.title} className="space-y-1.5 bg-surface p-5">
            <h2 className="text-base font-medium text-ink">{cell.title}</h2>
            <p className="text-sm leading-relaxed text-ink-muted">{cell.body}</p>
          </div>
        ))}
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-medium text-ink">Subject tags</h2>
        <div className="flex flex-wrap items-center gap-2">
          {swatches.map((swatch) => (
            <span
              key={swatch.name}
              className="inline-flex items-center gap-2 rounded-pill border border-border bg-surface px-3 py-1.5 text-sm text-ink-secondary"
            >
              <span className={`size-2.5 rounded-pill ${swatch.className}`} aria-hidden />
              {swatch.name}
            </span>
          ))}
        </div>
      </section>

      <footer className="space-y-1 border-t border-border pt-6 text-sm text-ink-muted">
        <p>Reminders will fire on your phone. The web dashboard shows them while it is open.</p>
        <p>Next: auth shell and the app frame (Phase 1).</p>
      </footer>
    </main>
  )
}
