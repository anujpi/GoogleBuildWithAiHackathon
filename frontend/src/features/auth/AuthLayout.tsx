import type { ReactNode } from 'react'
import { ProductMark } from '@/components/layout/Sidebar'

const coverage = [
  ['Farm', 'Location, soil and irrigation as recorded'],
  ['Evidence', 'Every value labelled observed, estimated or synthetic'],
  ['Outlook', 'Supply, demand and market risk as they come online'],
]

/** Frame for the signed-out pages: product context on the left, the form on the right. */
export function AuthLayout({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <div className="grid min-h-svh bg-background lg:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
      <title>{`${title} · Agri Intelligence`}</title>
      <aside className="hidden flex-col justify-between bg-sidebar p-8 text-sidebar-foreground lg:flex">
        <ProductMark />
        <div>
          <p className="text-lg font-semibold tracking-tight text-sidebar-accent-foreground">Decisions for a farm, grounded in its evidence.</p>
          <dl className="mt-6 flex flex-col divide-y divide-sidebar-border border-y border-sidebar-border">
            {coverage.map(([term, text]) => (
              <div key={term} className="grid grid-cols-[5.5rem_1fr] gap-3 py-3 text-sm">
                <dt className="font-medium text-sidebar-accent-foreground">{term}</dt>
                <dd className="text-sidebar-foreground/75">{text}</dd>
              </div>
            ))}
          </dl>
        </div>
        <p className="text-[11px] text-sidebar-foreground/60">Prototype build. Synthetic values are always labelled.</p>
      </aside>

      <main className="flex items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-sm">
          <div className="mb-8 rounded-md bg-sidebar p-3 lg:hidden">
            <ProductMark />
          </div>
          <h1 className="text-xl font-semibold tracking-tight md:text-2xl">{title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          <div className="mt-6">{children}</div>
        </div>
      </main>
    </div>
  )
}
