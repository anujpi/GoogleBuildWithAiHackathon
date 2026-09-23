import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router'
import {
  AvailabilityBadge,
  ConfidenceBadge,
  DataFreshness,
  DataOriginBadge,
  GapBadge,
  RiskIndicator,
  TrendLabel,
} from '@/components/data-display/status'
import { EmptyState } from '@/components/feedback/states'
import { Panel } from '@/components/layout/Panel'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatNumber, formatRelative } from '@/lib/format'
import type { Alert, CropPreview, SourceStatus } from './types'

const category: Record<Alert['category'], string> = {
  supply: 'Supply',
  weather: 'Weather',
  disease: 'Disease',
  market: 'Market',
  data: 'Data source',
}

export function AlertsPanel({ alerts }: { alerts: Alert[] }) {
  return (
    <Panel
      title="Active alerts"
      description={`${alerts.length} open for this region`}
      actions={
        <Link to="/alerts" className="inline-flex items-center gap-1 rounded-sm text-xs font-medium text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
          All alerts <ArrowRight className="size-3.5" aria-hidden />
        </Link>
      }
    >
      {alerts.length === 0 ? (
        <EmptyState message="No active alerts for this region." />
      ) : (
        <ul className="divide-y">
          {alerts.map((a) => (
            <li key={a.id} className="flex flex-col gap-1.5 px-4 py-3">
              <div className="flex items-center justify-between gap-2">
                <RiskIndicator level={a.severity} />
                <span className="text-xs text-muted-foreground">
                  {category[a.category]} · <time dateTime={a.raisedAt}>{formatRelative(a.raisedAt)}</time>
                </span>
              </div>
              <p className="text-sm font-medium">{a.title}</p>
              <p className="text-xs text-muted-foreground">
                {a.district} — {a.detail}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

export function SourcesPanel({ sources }: { sources: SourceStatus[] }) {
  return (
    <Panel title="Data freshness" description="Inputs behind the signals on this page">
      <ul className="divide-y">
        {sources.map((s) => (
          <li key={s.id} className="flex flex-col gap-1.5 px-4 py-2.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm">{s.name}</span>
              <AvailabilityBadge status={s.availability} />
            </div>
            <div className="flex items-center justify-between gap-2">
              <DataFreshness updatedAt={s.updatedAt} />
              <DataOriginBadge origin={s.origin} />
            </div>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

const level = { high: 'High', medium: 'Medium', low: 'Low' } as const

export function CropPreviewPanel({ crops }: { crops: CropPreview[] }) {
  return (
    <Panel
      title="Crop intelligence preview"
      description="Trade-offs for candidate crops on this farm. Not a ranking — rows are alphabetical."
      actions={
        <>
          <DataOriginBadge origin="synthetic" />
          <Link to="/crops" className="inline-flex items-center gap-1 rounded-sm text-xs font-medium text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
            Compare crops <ArrowRight className="size-3.5" aria-hidden />
          </Link>
        </>
      }
    >
      {crops.length === 0 ? (
        <EmptyState message="No candidate crops have been assessed for this farm yet." />
      ) : (
        <Table className="text-xs">
          <TableHeader>
            <TableRow>
              <TableHead className="pl-4">Crop</TableHead>
              <TableHead>Suitability</TableHead>
              <TableHead className="text-right">Expected yield</TableHead>
              <TableHead>Water need</TableHead>
              <TableHead>Weather risk</TableHead>
              <TableHead>Supply pressure</TableHead>
              <TableHead>Demand</TableHead>
              <TableHead>Projected gap</TableHead>
              <TableHead className="pr-4">Confidence</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {crops.map((c) => (
              <TableRow key={c.cropId}>
                <TableCell className="pl-4 text-sm font-medium">{c.crop}</TableCell>
                <TableCell>{level[c.suitability]}</TableCell>
                <TableCell className="tabular text-right">
                  {formatNumber(c.expectedYield.value)} <span className="text-muted-foreground">{c.expectedYield.unit}</span>
                </TableCell>
                <TableCell>{level[c.waterNeed]}</TableCell>
                <TableCell>
                  <RiskIndicator level={c.weatherRisk} />
                </TableCell>
                <TableCell>
                  <RiskIndicator level={c.supplyPressure} />
                </TableCell>
                <TableCell>
                  <TrendLabel value={c.demandTrend} />
                </TableCell>
                <TableCell>
                  <GapBadge state={c.projectedGap} />
                </TableCell>
                <TableCell className="pr-4">
                  <ConfidenceBadge value={c.confidence} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Panel>
  )
}
