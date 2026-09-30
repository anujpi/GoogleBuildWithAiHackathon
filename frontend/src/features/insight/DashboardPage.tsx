import { Plus } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { AvailabilityBadge } from '@/components/data-display/status'
import { Field } from '@/components/forms/Field'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { canOwnFarms } from '@/features/auth/api'
import { useAuth } from '@/features/auth/context'
import { useFarms } from '@/features/farms/hooks'
import type { Farm } from '@/features/farms/model'
import { describeError } from '@/lib/api/client'
import { formatDateTime } from '@/lib/format'
import { useReferenceScope } from './api'
import { CropEvidencePanel, RiskPanel, WeatherPanel } from './components'

function Stat({ label, children, note }: { label: string; children: ReactNode; note?: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 bg-card p-4">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="text-lg font-semibold">{children}</dd>
      {note && <dd className="text-xs text-muted-foreground">{note}</dd>}
    </div>
  )
}

function SupplyStatus() {
  const scope = useReferenceScope()
  if (scope.isPending) return <Stat label="Supply intelligence">…</Stat>
  if (scope.error) return <Stat label="Supply intelligence" note={describeError(scope.error, 'the reference scope')}><AvailabilityBadge status="unavailable" /></Stat>
  const n = scope.data.supplySeries.length
  return (
    <Stat
      label="Supply intelligence"
      note={n ? `${n} district × crop × season series · synced ${formatDateTime(scope.data.syncedAt!)}` : 'Reference data not loaded yet'}
    >
      {n ? (
        <Link to="/supply" className="text-sm font-medium underline-offset-2 hover:underline">
          Open supply intelligence
        </Link>
      ) : (
        <AvailabilityBadge status="unavailable" />
      )}
    </Stat>
  )
}

function FarmWorkspace({ farms }: { farms: Farm[] }) {
  const [id, setId] = useState(farms[0].id)
  const farm = farms.find((f) => f.id === id) ?? farms[0]
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <Field label="Farm" className="w-72 max-w-full">
          {(a) => (
            <NativeSelect {...a} className="w-full" value={farm.id} onChange={(e) => setId(e.target.value)}>
              {farms.map((f) => (
                <NativeSelectOption key={f.id} value={f.id}>
                  {f.name} {f.currentCrop ? `· ${f.currentCrop}` : ''}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          )}
        </Field>
        <Button asChild variant="outline" size="sm">
          <Link to={`/farms/${farm.id}`}>Open farm details</Link>
        </Button>
      </div>
      <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-2">
        <WeatherPanel key={`w-${farm.id}`} latitude={farm.location.latitude} longitude={farm.location.longitude} />
        <RiskPanel key={`r-${farm.id}`} farm={farm} />
      </div>
      <CropEvidencePanel key={`e-${farm.id}`} farm={farm} />
    </div>
  )
}

function FarmerDashboard() {
  const farms = useFarms()
  const list = farms.data
  return (
    <>
      <dl aria-label="Summary" className="flex flex-wrap gap-px border bg-border *:min-w-0 *:flex-1 *:basis-48">
        <Stat label="Farms">{list ? list.length : '…'}</Stat>
        <Stat label="Current crops" note="As recorded on each farm">
          <span className="text-sm">{list ? [...new Set(list.map((f) => f.currentCrop).filter(Boolean))].join(', ') || 'Not recorded' : '…'}</span>
        </Stat>
        <Stat label="Farms with intelligence" note="District in the served scope">
          {list ? `${list.filter((f) => f.intelligenceSupported).length} of ${list.length}` : '…'}
        </Stat>
        <SupplyStatus />
      </dl>

      {farms.isPending ? (
        <LoadingState message="Loading farms..." />
      ) : farms.error ? (
        <ErrorState message={describeError(farms.error, 'the farm list')} onRetry={() => farms.refetch()} />
      ) : list!.length === 0 ? (
        <Panel title="Farms">
          <EmptyState message="No farms have been added yet.">
            <Button asChild className="mt-2">
              <Link to="/farms/new">
                <Plus aria-hidden /> Add Farm
              </Link>
            </Button>
          </EmptyState>
        </Panel>
      ) : (
        <FarmWorkspace farms={list!} />
      )}
    </>
  )
}

export function DashboardPage() {
  const { currentUser } = useAuth()
  const owner = currentUser && canOwnFarms(currentUser.role)
  return (
    <div className="flex flex-col gap-5">
      <title>Dashboard · Agri Intelligence</title>
      <PageHeader title="Dashboard" description="Your farms with their weather, crop evidence and production risk, from the backend. Nothing here is synthetic." />
      {owner ? (
        <FarmerDashboard />
      ) : (
        <>
          <dl className="flex flex-wrap gap-px border bg-border *:min-w-0 *:flex-1 *:basis-48">
            <SupplyStatus />
          </dl>
          <Panel title="Farms">
            <EmptyState message="Farm records are owned by farmers and FPOs. Your role works with supply intelligence." />
          </Panel>
        </>
      )}
    </div>
  )
}
