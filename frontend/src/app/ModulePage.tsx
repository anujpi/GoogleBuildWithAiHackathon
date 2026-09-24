import { CalendarClock } from 'lucide-react'
import { Link } from 'react-router'
import type { NavItem } from '@/app/navigation'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'

/** Route target for modules that are not built yet. Honest about it rather than faking a screen. */
export function ModulePage({ item }: { item: NavItem }) {
  const Icon = item.icon
  return (
    <div className="flex flex-col gap-6">
      <title>{`${item.label} · Agri Intelligence`}</title>
      <PageHeader title={item.label} description={item.summary} />
      <Panel title="Module status">
        <div className="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-md border bg-muted">
            <Icon className="size-5 text-muted-foreground" aria-hidden />
          </div>
          <div className="flex-1">
            <p className="flex items-center gap-1.5 text-sm font-medium">
              <CalendarClock className="size-4 text-muted-foreground" aria-hidden />
              Scheduled for phase {item.phase}
            </p>
            <p className="mt-1 max-w-xl text-sm text-muted-foreground">
              The route and navigation are in place. Screens, data contracts and states for this module are built when its phase begins.
            </p>
          </div>
          <Button asChild variant="outline" size="sm">
            <Link to="/dashboard">Back to dashboard</Link>
          </Button>
        </div>
      </Panel>
    </div>
  )
}

export function NotFoundPage() {
  return (
    <div className="flex flex-col gap-6">
      <title>Not found · Agri Intelligence</title>
      <PageHeader title="Page not found" description="This address does not match any module." />
      <div>
        <Button asChild variant="outline" size="sm">
          <Link to="/dashboard">Back to dashboard</Link>
        </Button>
      </div>
    </div>
  )
}
