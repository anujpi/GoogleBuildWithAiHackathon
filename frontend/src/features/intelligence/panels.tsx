import { CircleHelp, ImageUp, LoaderCircle, Sparkles, Square, Volume2 } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from 'cn'
import { MetricPanel } from '@/components/data-display/MetricPanel'
import { DataOriginBadge, GapBadge, RiskIndicator } from '@/components/data-display/status'
import { Callout } from '@/components/feedback/Callout'
import { Panel } from '@/components/layout/Panel'
import { Button } from '@/components/ui/button'
import { describeError } from '@/lib/api/client'
import { formatDateTime, formatNumber, formatPercent } from '@/lib/format'
import type { DataOrigin } from '@/types/status'
import { useAdvisory, useDiagnose } from './hooks'
import { speak, stopSpeaking, type Strings } from './i18n'
import type { Classification, Diagnosis, IntelligenceReport, Language, Level, Section } from './types'

const origin: Record<Classification, DataOrigin> = {
  OBSERVED: 'observed',
  FORECAST: 'forecast',
  MODEL_PREDICTION: 'model',
  ESTIMATED: 'estimate',
  SYNTHETIC: 'synthetic',
}

export function Origin({ value }: { value: Classification | null | undefined }) {
  return value ? <DataOriginBadge origin={origin[value]} /> : null
}

export function LevelBadge({ level }: { level: Level }) {
  if (level === 'UNKNOWN')
    return (
      <span className="inline-flex h-6 items-center gap-1.5 rounded-sm border bg-muted px-2 text-xs font-medium text-muted-foreground">
        <CircleHelp className="size-3.5" aria-hidden /> Unknown
      </span>
    )
  return <RiskIndicator level={level === 'HIGH' ? 'high' : level === 'MODERATE' ? 'moderate' : 'low'} />
}

/** Tonnes shown in lakh tonnes (the unit Indian agricultural statistics use), with the exact value in the title. */
const lakhT = (t: number) => `${formatNumber(t / 1e5)}`

function Unavailable({ section, what }: { section: Section<unknown>; what: string }) {
  return (
    <Callout tone="caution" title={`${what} unavailable`}>
      {section.errorMessage ?? 'No reason given'} {section.errorCode && <span className="font-mono">({section.errorCode})</span>}
    </Callout>
  )
}

function Source({ children }: { children: ReactNode }) {
  return <p className="text-[11px] leading-snug text-muted-foreground">{children}</p>
}

// --- current conditions ------------------------------------------------------------------------

export function ConditionsPanel({ r, t }: { r: IntelligenceReport; t: Strings }) {
  const w = r.weather
  return (
    <Panel title={t.conditions} description={t.conditionsDesc} actions={<Origin value={w.dataClassification} />}>
      {w.status !== 'OK' || !w.current || !w.next7Days ? (
        <div className="p-4">
          <Callout tone="caution" title="Live weather unavailable">
            {w.unavailableReason} — no values are estimated in its place.
          </Callout>
        </div>
      ) : (
        <>
          <dl className="grid grid-cols-2 gap-px bg-border">
            <MetricPanel className="bg-card" label={t.temperature} value={w.current.temperatureC ?? '—'} unit="°C" />
            <MetricPanel className="bg-card" label={t.humidity} value={w.current.relativeHumidityPct ?? '—'} unit="%" />
            <MetricPanel className="bg-card" label={t.rain7} value={formatNumber(w.next7Days.totalPrecipitationMm)} unit="mm" />
            <MetricPanel className="bg-card" label={t.maxTemp7} value={w.next7Days.maxTemperatureC ?? '—'} unit="°C" />
          </dl>
          <div className="border-t px-4 py-2">
            <Source>
              {w.source} · fetched {formatDateTime(w.fetchedAt)} · {r.farm.latitude}, {r.farm.longitude}
            </Source>
          </div>
        </>
      )}
    </Panel>
  )
}

// --- decision ----------------------------------------------------------------------------------

const decisionTone: Record<string, 'positive' | 'caution' | 'critical' | 'info'> = {
  SUITABLE_CANDIDATE: 'positive',
  SUITABLE_WITH_MARKET_RISK: 'caution',
  REVIEW: 'caution',
  NOT_RECOMMENDED: 'critical',
  INSUFFICIENT_EVIDENCE: 'info',
}

export function DecisionPanel({ r, t }: { r: IntelligenceReport; t: Strings }) {
  const d = r.decision
  return (
    <Panel title={t.decision} description={t.decisionDesc} actions={<LevelBadge level={r.risk.overall} />}>
      <div className="flex flex-col gap-3 p-4">
        <Callout tone={decisionTone[d.status] ?? 'info'} title={d.headline}>
          <span className="font-mono text-[11px]">{d.status}</span>
        </Callout>
        <ul className="flex list-disc flex-col gap-1 pl-5 text-sm">
          {d.reasons.map((x) => (
            <li key={x}>{x}</li>
          ))}
        </ul>
        {d.alternatives.length > 0 && (
          <div className="text-sm">
            <span className="text-xs font-medium text-muted-foreground">{t.alternatives}: </span>
            {d.alternatives.join(' · ')}
          </div>
        )}
        <Source>
          Focus crop: {r.focusCrop ?? '—'} ({r.focusCropReason}) · decided by {d.decidedBy}
        </Source>
      </div>
    </Panel>
  )
}

// --- suitability -------------------------------------------------------------------------------

export function SuitabilityPanel({ r, t, onPick }: { r: IntelligenceReport; t: Strings; onPick: (crop: string) => void }) {
  const s = r.suitability
  return (
    <Panel title={t.suitability} description={t.suitabilityDesc} actions={s.data && <Origin value={s.data.provenance.dataClassification} />}>
      {!s.data ? (
        <div className="p-4">
          <Unavailable section={s} what="Crop suitability" />
        </div>
      ) : (
        <div className="flex flex-col">
          <ol className="divide-y">
            {s.data.candidates.slice(0, 6).map((c) => {
              const focus = c.crop === r.focusCrop
              return (
                <li key={c.crop}>
                  <button
                    type="button"
                    onClick={() => onPick(c.crop)}
                    aria-pressed={focus}
                    className={cn('grid w-full grid-cols-[8rem_minmax(0,1fr)_3rem] items-center gap-3 px-4 py-2.5 text-left hover:bg-muted/50', focus && 'bg-muted')}
                  >
                    <span className="truncate text-sm font-medium">{c.crop}</span>
                    <span className="flex min-w-0 flex-col gap-1">
                      <span className="relative h-1.5 overflow-hidden rounded-full bg-border" aria-hidden>
                        <span className="absolute inset-y-0 left-0 bg-primary" style={{ width: `${c.suitabilityScore * 100}%` }} />
                      </span>
                      <span className="truncate text-[11px] text-muted-foreground">{c.evidence[0]}</span>
                    </span>
                    <span className="tabular text-right text-sm">{c.suitabilityScore.toFixed(2)}</span>
                  </button>
                </li>
              )
            })}
          </ol>
          <div className="flex flex-col gap-1 border-t px-4 py-2">
            <Source>
              {s.data.scoreType}. {s.data.cropsConsidered} crops considered for {s.data.region} · {s.data.season}. Click a crop to focus on it.
            </Source>
            <Source>Sources: {s.data.provenance.dataSources.join('; ')}</Source>
          </div>
        </div>
      )}
    </Panel>
  )
}

// --- supply / demand / gap ---------------------------------------------------------------------

export function MarketPanel({ r, t }: { r: IntelligenceReport; t: Strings }) {
  const { supply, demand, demandOutlook, gap } = r
  const g = gap.data
  return (
    <Panel title={t.supplyDemand} description={t.supplyDemandDesc} actions={<span className="text-xs text-muted-foreground">{r.farm.state} · {r.focusCrop}</span>}>
      <dl className="grid grid-cols-1 gap-px bg-border sm:grid-cols-2 xl:grid-cols-4">
        {supply.data ? (
          <MetricPanel
            className="bg-card"
            label={`${t.supply} · ${supply.data.cropYear}`}
            value={lakhT(supply.data.forecast.expectedProductionTonnes)}
            unit="lakh t"
            status={<Origin value={supply.data.forecast.dataClassification} />}
            footnote={
              supply.data.forecast.predictionInterval &&
              `80% interval ${lakhT(supply.data.forecast.predictionInterval.lowerTonnes)}–${lakhT(supply.data.forecast.predictionInterval.upperTonnes)} lakh t (test coverage ${formatPercent(supply.data.forecast.predictionInterval.testEmpiricalCoverage)}) · ${supply.data.model.modelName} ${supply.data.model.modelVersion}`
            }
          />
        ) : (
          <div className="bg-card p-4">
            <Unavailable section={supply} what="Supply forecast" />
          </div>
        )}
        {demand.data ? (
          <MetricPanel
            className="bg-card"
            label={`${t.demand} · ${g?.year ?? ''}`}
            value={lakhT(demand.data.forecast)}
            unit="lakh t"
            status={<Origin value={demand.data.dataClassification} />}
            footnote={`${demand.data.method}; national × Census 2011 population share`}
          />
        ) : (
          <div className="bg-card p-4">
            <Unavailable section={demand} what="Demand" />
          </div>
        )}
        {g ? (
          <MetricPanel
            className="bg-card"
            label={`${t.gap} · ${g.year}`}
            value={`${g.gapTonnes > 0 ? '+' : ''}${lakhT(g.gapTonnes)}`}
            unit={`lakh t (${g.gapPctOfDemand > 0 ? '+' : ''}${g.gapPctOfDemand}%)`}
            status={
              <>
                <GapBadge state={g.status === 'SURPLUS' ? 'surplus' : g.status === 'DEFICIT' ? 'shortage' : 'balanced'} />
                <Origin value="ESTIMATED" />
              </>
            }
            footnote={g.caveats[0]}
          />
        ) : (
          <div className="bg-card p-4">
            <Unavailable section={gap} what="Gap" />
          </div>
        )}
        {demandOutlook.data ? (
          <MetricPanel
            className="bg-card"
            label={`${t.outlook} · ${demandOutlook.data.period.match(/\d{4}/)?.[0] ?? ''}`}
            value={lakhT(demandOutlook.data.forecast)}
            unit="lakh t"
            status={<Origin value={demandOutlook.data.dataClassification} />}
            footnote={
              demandOutlook.data.interval
                ? `range ${lakhT(demandOutlook.data.interval.lower)}–${lakhT(demandOutlook.data.interval.upper)} lakh t · backtest MAPE ${demandOutlook.data.backtests.map((b) => `${b.method.split(':')[0]} ${b.mapePct}%`).join(', ')}`
                : demandOutlook.data.method
            }
          />
        ) : (
          <div className="bg-card p-4">
            <Unavailable section={demandOutlook} what="Demand outlook" />
          </div>
        )}
      </dl>
      <div className="flex flex-col gap-1 border-t px-4 py-2">
        {g && <Source>{g.method}. {g.caveats.slice(1).join(' ')}</Source>}
        {demand.data && <Source>Demand source: {demand.data.provenance.dataSources.join('; ')}</Source>}
        {supply.data && (
          <Source>
            Supply: {supply.data.model.trainingDataSource}; trained {supply.data.model.trainingPeriod}, tested {supply.data.model.evaluationPeriod}. Area assumption: {formatNumber(supply.data.targetAreaHectares)} ha (last observed year).
          </Source>
        )}
      </div>
    </Panel>
  )
}

// --- risk + anomalies --------------------------------------------------------------------------

export function RiskPanel({ r, t }: { r: IntelligenceReport; t: Strings }) {
  return (
    <Panel title={t.risk} description={t.riskDesc} actions={<LevelBadge level={r.risk.overall} />}>
      <ul className="divide-y">
        {r.risk.factors.map((f) => (
          <li key={f.category} className="flex items-start gap-3 px-4 py-2.5">
            <span className="w-24 shrink-0">
              <LevelBadge level={f.level} />
            </span>
            <span className="min-w-0 text-sm">
              <span className="font-medium">{f.category.replaceAll('_', ' ').toLowerCase()}</span>
              <span className="block text-xs text-muted-foreground">{f.reason}</span>
            </span>
          </li>
        ))}
      </ul>
      <div className="border-t">
        <h3 className="px-4 pt-3 text-xs font-medium text-muted-foreground">Anomaly checks</h3>
        <ul className="divide-y">
          {r.anomalies.map((a) => (
            <li key={a.metric} className="flex items-start justify-between gap-3 px-4 py-2.5 text-sm">
              <span className="min-w-0">
                {a.label}
                <span className="block text-[11px] text-muted-foreground">{a.source}</span>
              </span>
              {a.result.data ? (
                <span className="shrink-0 text-right">
                  <span className={cn('text-xs font-medium', a.result.data.status === 'ANOMALY' ? 'text-warning' : 'text-muted-foreground')}>
                    {a.result.data.status} {a.result.data.direction !== 'NONE' && a.result.data.direction}
                  </span>
                  <span className="tabular block text-[11px] text-muted-foreground">
                    {a.result.data.period}: {a.result.data.deviationPct != null ? `${a.result.data.deviationPct > 0 ? '+' : ''}${a.result.data.deviationPct.toFixed(1)}% vs median` : '—'}
                    {a.result.data.robustZ != null && `, z ${a.result.data.robustZ.toFixed(1)}`}
                  </span>
                </span>
              ) : (
                <span className="text-xs text-muted-foreground">{a.result.errorCode}</span>
              )}
            </li>
          ))}
        </ul>
        <div className="border-t px-4 py-2">
          <Source>{r.risk.method}</Source>
        </div>
      </div>
    </Panel>
  )
}

// --- crop doctor -------------------------------------------------------------------------------

export function DoctorPanel({ t, crop, diagnosis, onDiagnosis }: { t: Strings; crop: string | null; diagnosis: Diagnosis | null; onDiagnosis: (d: Diagnosis | null) => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const diagnose = useDiagnose()
  const input = useRef<HTMLInputElement>(null)

  // Revoke the last preview URL when the panel unmounts.
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview)
  }, [preview])

  const run = () => file && diagnose.mutate({ image: file, crop }, { onSuccess: onDiagnosis })
  const d = diagnosis?.ml

  return (
    <Panel title={t.doctor} description={t.doctorDesc} actions={d && <Origin value={d.dataClassification} />}>
      <div className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png"
            className="sr-only"
            onChange={(e) => {
              const f = e.target.files?.[0] ?? null
              setFile(f)
              setPreview(f ? URL.createObjectURL(f) : null)
              onDiagnosis(null)
            }}
          />
          <Button variant="outline" size="sm" onClick={() => input.current?.click()}>
            <ImageUp aria-hidden /> {t.upload}
          </Button>
          <Button size="sm" disabled={!file || diagnose.isPending} onClick={run}>
            {diagnose.isPending && <LoaderCircle className="animate-spin" aria-hidden />} {t.diagnose}
          </Button>
          {file && <span className="truncate text-xs text-muted-foreground">{file.name}</span>}
        </div>
        {diagnose.error && (
          <Callout tone="critical" role="alert" title="Diagnosis failed">
            {describeError(diagnose.error, 'the diagnosis')}
          </Callout>
        )}
        <div className="flex gap-4">
          {preview && <img src={preview} alt="Uploaded leaf" className="size-28 shrink-0 rounded-sm border object-cover" />}
          {d && diagnosis && (
            <div className="flex min-w-0 flex-col gap-2 text-sm">
              <div>
                <span className="text-lg font-semibold">{d.prediction.disease.replaceAll('_', ' ')}</span>
                <span className="text-muted-foreground"> · {d.prediction.crop}</span>
              </div>
              <div className="text-xs text-muted-foreground">
                model probability <span className="tabular font-medium text-foreground">{formatPercent(d.prediction.probability)}</span> (uncalibrated softmax) ·{' '}
                {d.modelName} {d.modelVersion}
              </div>
              <ul className="text-xs text-muted-foreground">
                {d.topClasses.slice(1).map((c) => (
                  <li key={c.classLabel}>
                    {c.crop} · {c.disease.replaceAll('_', ' ')} — {formatPercent(c.probability)}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
        {diagnosis?.needsExpertReview && (
          <Callout tone="caution" title="Uncertain result — confirm with an expert">
            {diagnosis.reviewPolicy}
          </Callout>
        )}
        {diagnosis?.cropWarning && <Callout tone="caution" title="Crop not covered">{diagnosis.cropWarning}</Callout>}
        {diagnosis && (
          <ul className="flex list-disc flex-col gap-0.5 pl-4 text-[11px] text-muted-foreground">
            {diagnosis.limitations.map((l) => (
              <li key={l}>{l}</li>
            ))}
            <li>
              Training data: {d?.provenance.trainingDataSource} ({d?.provenance.trainingDataLicense})
            </li>
          </ul>
        )}
      </div>
    </Panel>
  )
}

// --- AI advisory -------------------------------------------------------------------------------

export function AdvisoryPanel({ r, t, language, diagnosis }: { r: IntelligenceReport; t: Strings; language: Language; diagnosis: Diagnosis | null }) {
  const advisory = useAdvisory()
  const [speaking, setSpeaking] = useState(false)
  const a = advisory.data
  const stale = a && a.language !== language

  useEffect(() => () => stopSpeaking(), [])

  const run = () => {
    stopSpeaking()
    setSpeaking(false)
    advisory.mutate({
      farmId: r.farm.id,
      crop: r.focusCrop,
      language,
      disease: diagnosis
        ? {
            crop: diagnosis.ml.prediction.crop,
            disease: diagnosis.ml.prediction.disease,
            probability: diagnosis.ml.prediction.probability,
            modelVersion: diagnosis.ml.modelVersion,
            needsExpertReview: diagnosis.needsExpertReview,
          }
        : null,
    })
  }

  const readAloud = () => {
    if (!a) return
    if (speaking) {
      stopSpeaking()
      setSpeaking(false)
      return
    }
    const text = [a.explanation, `${t.actions}.`, ...a.recommendedActions].join(' ')
    if (speak(text, a.language, () => setSpeaking(false))) setSpeaking(true)
  }

  return (
    <Panel
      title={t.advisory}
      description={t.advisoryDesc}
      actions={
        <>
          {a && (
            <span className={cn('inline-flex h-6 items-center gap-1.5 rounded-sm border px-2 text-xs font-medium', a.aiGenerated ? 'border-border bg-muted' : 'border-caution/30 bg-caution/10 text-caution')}>
              {a.aiGenerated ? <Sparkles className="size-3.5" aria-hidden /> : null}
              {a.aiGenerated ? `Google Gemini · ${a.model}` : 'Template fallback (not AI)'}
            </span>
          )}
          {a && (
            <Button variant="outline" size="sm" onClick={readAloud}>
              {speaking ? <Square aria-hidden /> : <Volume2 aria-hidden />} {speaking ? t.stop : t.readAloud}
            </Button>
          )}
          <Button size="sm" onClick={run} disabled={advisory.isPending}>
            {advisory.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />} {t.explain}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-4">
        {advisory.error && (
          <Callout tone="critical" role="alert" title="Explanation failed">
            {describeError(advisory.error, 'the explanation')}
          </Callout>
        )}
        {!a && !advisory.isPending && <p className="text-sm text-muted-foreground">{t.advisoryDesc}</p>}
        {a && (
          <>
            {stale && <Callout tone="info" title="Language changed — press the button again for a new explanation." />}
            {a.fallbackReason && <Callout tone="synthetic" title="AI unavailable — showing a rule-based template">{a.fallbackReason}</Callout>}
            <p lang={a.language} className="text-base leading-relaxed">
              {a.explanation}
            </p>
            <div className="grid gap-4 md:grid-cols-3">
              <List lang={a.language} title={t.keyFactors} items={a.keyFactors} />
              <List lang={a.language} title={t.actions} items={a.recommendedActions} />
              <List lang={a.language} title={t.uncertainty} items={a.uncertainty} />
            </div>
            {a.numbersNotFoundInEvidence.length > 0 && (
              <Callout tone="caution" title="Some numbers in this text could not be matched to the evidence">
                {a.numbersNotFoundInEvidence.join(', ')} — treat them with caution.
              </Callout>
            )}
            <details className="text-xs text-muted-foreground">
              <summary className="cursor-pointer">Evidence sent to the model ({a.groundingNote})</summary>
              <pre className="mt-2 max-h-80 overflow-auto rounded-sm border bg-muted/40 p-2 text-[11px]">{JSON.stringify(a.evidence, null, 2)}</pre>
            </details>
          </>
        )}
      </div>
    </Panel>
  )
}

function List({ title, items, lang }: { title: string; items: string[]; lang: string }) {
  return (
    <div>
      <h3 className="mb-1 text-xs font-medium text-muted-foreground">{title}</h3>
      <ul lang={lang} className="flex list-disc flex-col gap-1 pl-4 text-sm">
        {items.map((x) => (
          <li key={x}>{x}</li>
        ))}
      </ul>
    </div>
  )
}
