import { Plus } from 'lucide-react'
import { Link } from 'react-router'
import { AsyncContent, EmptyState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { describeError } from '@/lib/api/client'
import { formatNumber, formatRelative } from '@/lib/format'
import { AvailabilityBadge } from '@/components/data-display/status'
import { ClassificationBadge } from './components'
import { useFarms } from './hooks'
import { areaUnitLabel, formatCoords, irrigationLabel, locationLabel, seasonLabel, soilOf, soilSourceLabel, type Farm } from './model'

function AddFarmButton() {
  return (
    <Button asChild>
      <Link to="/farms/new">
        <Plus aria-hidden /> Add Farm
      </Link>
    </Button>
  )
}

function SoilCell({ soil }: { soil: Farm['soilProfile'] }) {
  if (!soil) return <AvailabilityBadge status="unavailable" />
  return (
    <span className="flex flex-col items-start gap-1">
      <ClassificationBadge value={soil.dataClassification} />
      <span className="text-xs text-muted-foreground">{soilSourceLabel[soil.source].label}</span>
    </span>
  )
}

function FarmsTable({ farms }: { farms: Farm[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="pl-4">Farm</TableHead>
          <TableHead className="hidden sm:table-cell">Location</TableHead>
          <TableHead className="text-right">Area</TableHead>
          <TableHead className="hidden md:table-cell">Crop</TableHead>
          <TableHead className="hidden lg:table-cell">Season</TableHead>
          <TableHead className="hidden xl:table-cell">Irrigation</TableHead>
          <TableHead className="hidden lg:table-cell">Soil data</TableHead>
          <TableHead className="hidden pr-4 text-right xl:table-cell">Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {farms.map((f) => (
          // The name link stretches over the whole row, so any click opens the farm while the markup stays one link.
          <TableRow key={f.id} className="relative cursor-pointer has-[a:focus-visible]:bg-muted">
            <TableCell className="max-w-64 py-3 pl-4">
              <Link to={`/farms/${f.id}`} className="block truncate font-medium outline-none after:absolute after:inset-0 hover:underline">
                {f.name}
              </Link>
              <span className="block truncate text-xs text-muted-foreground sm:hidden">{locationLabel(f.location)}</span>
            </TableCell>
            <TableCell className="hidden max-w-72 sm:table-cell">
              <span className="block truncate">{locationLabel(f.location)}</span>
              <span className="tabular block text-xs text-muted-foreground">{formatCoords(f.location.latitude, f.location.longitude)}</span>
            </TableCell>
            <TableCell className="tabular text-right whitespace-nowrap">
              {formatNumber(f.area)} <span className="text-xs text-muted-foreground">{areaUnitLabel[f.areaUnit].short}</span>
            </TableCell>
            <TableCell className="hidden md:table-cell">
              {f.currentCrop ?? <span className="text-muted-foreground">—</span>}
              {f.previousCrop && <span className="block text-xs text-muted-foreground">after {f.previousCrop}</span>}
            </TableCell>
            <TableCell className="hidden lg:table-cell">{seasonLabel[f.season].label}</TableCell>
            <TableCell className="hidden xl:table-cell">{irrigationLabel[f.irrigationType]}</TableCell>
            <TableCell className="hidden lg:table-cell">
              <SoilCell soil={soilOf(f)} />
            </TableCell>
            <TableCell className="hidden pr-4 text-right text-xs whitespace-nowrap text-muted-foreground xl:table-cell">
              <time dateTime={f.updatedAt}>{formatRelative(f.updatedAt)}</time>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export function FarmsPage() {
  const farms = useFarms()
  const count = farms.data?.length

  return (
    <div className="flex flex-col gap-5">
      <title>Farms · Agri Intelligence</title>
      <PageHeader title="Farms" description="Registered farms, their location, crop and the provenance of their soil data." actions={<AddFarmButton />} />
      <Panel title="All farms" description={count === undefined ? undefined : `${count} ${count === 1 ? 'farm' : 'farms'}`}>
        <AsyncContent
          query={farms}
          loadingMessage="Loading farms..."
          errorMessage={describeError(farms.error, 'the farm list')}
          isEmpty={(list) => list.length === 0}
          empty={
            <EmptyState message="No farms have been added yet.">
              <p className="max-w-sm text-xs">Add a farm with its location, crop and soil test to start building its intelligence profile.</p>
              <div className="mt-2">
                <AddFarmButton />
              </div>
            </EmptyState>
          }
        >
          {(list) => <FarmsTable farms={list} />}
        </AsyncContent>
      </Panel>
    </div>
  )
}
