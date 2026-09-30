import { Globe2, LoaderCircle, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { DataFreshness } from '@/components/data-display/status'
import { Callout } from '@/components/feedback/Callout'
import { AsyncContent, EmptyState, ErrorState } from '@/components/feedback/states'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { describeError } from '@/lib/api/client'
import { useFarms } from '@/features/farms/hooks'
import { dictionaries } from './i18n'
import { useIntelligence } from './hooks'
import { AdvisoryPanel, ConditionsPanel, DecisionPanel, DoctorPanel, MarketPanel, RiskPanel, SuitabilityPanel } from './panels'
import type { Diagnosis, Language } from './types'

function Field({ id, label, children }: { id: string; label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-muted-foreground">
        {label}
      </label>
      {children}
    </div>
  )
}

/** The end-to-end journey: farm → conditions → suitability → crop health → supply/demand/gap → risk → AI advisory. */
export function IntelligencePage() {
  const [params, setParams] = useSearchParams()
  const farms = useFarms()
  const language: Language = params.get('lang') === 'hi' ? 'hi' : 'en'
  const t = dictionaries[language]
  const farmId = params.get('farm') ?? farms.data?.[0]?.id ?? null
  const crop = params.get('crop')
  const report = useIntelligence(farmId, crop)
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null)

  const set = (key: string, value: string | null) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value) next.set(key, value)
        else next.delete(key)
        if (key === 'farm') next.delete('crop')
        return next
      },
      { replace: true },
    )

  const cropOptions = report.data?.suitability.data?.candidates.map((c) => c.crop) ?? []
  const focus = report.data?.focusCrop ?? null
  if (focus && !cropOptions.includes(focus)) cropOptions.unshift(focus)

  return (
    <div className="flex flex-col gap-5" lang={language}>
      <title>Farm intelligence · Agri Intelligence</title>
      <PageHeader
        title={t.title}
        description={t.description}
        actions={
          farms.data &&
          farms.data.length > 0 && (
            <>
              <Field id="ii-farm" label={t.farm}>
                <NativeSelect id="ii-farm" size="sm" value={farmId ?? ''} onChange={(e) => set('farm', e.target.value)} className="min-w-48 bg-card">
                  {farms.data.map((f) => (
                    <NativeSelectOption key={f.id} value={f.id}>
                      {f.name} · {f.location.district}, {f.location.state}
                    </NativeSelectOption>
                  ))}
                </NativeSelect>
              </Field>
              <Field id="ii-crop" label={t.crop}>
                <NativeSelect id="ii-crop" size="sm" value={focus ?? ''} onChange={(e) => set('crop', e.target.value)} className="min-w-36 bg-card">
                  {cropOptions.map((c) => (
                    <NativeSelectOption key={c} value={c}>
                      {c}
                    </NativeSelectOption>
                  ))}
                </NativeSelect>
              </Field>
              <Field id="ii-lang" label={t.language}>
                <NativeSelect id="ii-lang" size="sm" value={language} onChange={(e) => set('lang', e.target.value)} className="bg-card">
                  <NativeSelectOption value="en">English</NativeSelectOption>
                  <NativeSelectOption value="hi">हिन्दी</NativeSelectOption>
                </NativeSelect>
              </Field>
            </>
          )
        }
      />

      {farms.error ? (
        <ErrorState message={describeError(farms.error, 'your farms')} onRetry={() => farms.refetch()} />
      ) : farms.data && farms.data.length === 0 ? (
        <EmptyState message={t.noFarms}>
          <Button asChild>
            <Link to="/farms/new">
              <Plus aria-hidden /> {t.addFarm}
            </Link>
          </Button>
        </EmptyState>
      ) : (
        <AsyncContent query={{ ...report, isPending: farms.isPending || report.isPending }} loadingMessage="Computing farm intelligence…" errorMessage={report.error ? describeError(report.error, 'farm intelligence') : 'Farm intelligence is unavailable.'}>
          {(r) => (
            <>
              <div className="-mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <DataFreshness updatedAt={r.generatedAt} />
                <span>
                  {t.region}: {r.farm.district}, {r.farm.state} · {r.farm.sourceSeasonLabel} · {r.farm.areaHectares} ha · irrigation {r.farm.irrigationType?.toLowerCase().replace('_', '-') ?? 'unknown'} · soil pH {r.farm.soilPh ?? 'not recorded'}
                  {r.farm.soilDataClassification && ` (${r.farm.soilDataClassification.toLowerCase()})`}
                </span>
                {report.isFetching && <LoaderCircle className="size-3.5 animate-spin" aria-label="Refreshing" />}
              </div>
              <Callout tone="info" title={t.scale}>
                <span className="inline-flex items-center gap-1">
                  <Globe2 className="size-3.5" aria-hidden /> Each request is keyed by state, district, season and crop; add a farm in another state to see its own evidence.
                </span>
              </Callout>

              <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_26rem]">
                <DecisionPanel r={r} t={t} />
                <ConditionsPanel r={r} t={t} />
                <SuitabilityPanel r={r} t={t} onPick={(c) => set('crop', c)} />
                <DoctorPanel t={t} crop={r.focusCrop} diagnosis={diagnosis} onDiagnosis={setDiagnosis} />
              </div>
              <MarketPanel r={r} t={t} />
              <div className="grid grid-cols-1 gap-4 xl:grid-cols-[26rem_minmax(0,1fr)]">
                <RiskPanel r={r} t={t} />
                <AdvisoryPanel r={r} t={t} language={language} diagnosis={diagnosis} />
              </div>
              <section className="border bg-card px-4 py-3">
                <h2 className="text-xs font-medium text-muted-foreground">{t.sources}</h2>
                <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-4 text-xs text-muted-foreground">
                  {r.dataNotices.map((n) => (
                    <li key={n}>{n}</li>
                  ))}
                </ul>
              </section>
            </>
          )}
        </AsyncContent>
      )}
    </div>
  )
}
