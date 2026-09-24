import { useFormContext, useWatch, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { DataOriginBadge } from '@/components/data-display/status'
import { Callout } from '@/components/feedback/Callout'
import { ChoiceGroup } from '@/components/forms/ChoiceGroup'
import { Field } from '@/components/forms/Field'
import { Input } from '@/components/ui/input'
import { classificationLabel, countReadings, SOIL_CLASSIFICATIONS, SOIL_GROUPS, SOIL_METRICS, SOIL_SOURCES, soilSourceLabel } from '../model'
import type { FarmFormValues } from '../schema'

export function SoilStep() {
  const { register, formState: { errors, defaultValues } } = useFormContext<FarmFormValues>()
  const soil = useWatch<FarmFormValues, 'soilProfile'>({ name: 'soilProfile' })
  const soilData = useWatch<FarmFormValues, 'soilData'>({ name: 'soilData' })
  const e = errors.soilProfile
  const entered = countReadings(soil)
  const hadSoil = defaultValues?.soilData === 'provided' && Boolean(defaultValues.soilProfile?.source)

  return (
    <div className="flex flex-col gap-7">
      <ChoiceGroup
        legend="Soil data for this farm"
        columns={2}
        register={register('soilData')}
        error={errors.soilData?.message}
        options={[
          { value: 'provided', label: 'I have soil data', hint: 'Soil Health Card, lab report or other values' },
          { value: 'unavailable', label: 'No soil data yet', hint: 'Saved as unavailable. Nothing is estimated.' },
        ]}
      />

      {soilData === 'unavailable' ? (
        hadSoil ? (
          <Callout tone="caution" title="Saving will remove this farm's soil profile" className="-mt-4">
            The stored soil values will be deleted and soil will show as unavailable. Choose "I have soil data" to keep them.
          </Callout>
        ) : (
          <Callout tone="info" title="Soil will be recorded as unavailable" className="-mt-4">
            No values are filled in or guessed. You can add soil data later by editing the farm. Soil-dependent intelligence will report lower
            confidence until then.
          </Callout>
        )
      ) : (
        <SoilFields soil={soil} entered={entered} e={e} register={register} />
      )}
    </div>
  )
}

function SoilFields({
  soil,
  entered,
  e,
  register,
}: {
  soil: FarmFormValues['soilProfile']
  entered: number
  e: FieldErrors<FarmFormValues>['soilProfile']
  register: UseFormRegister<FarmFormValues>
}) {
  return (
    <div className="flex flex-col gap-7">
      <Callout tone="info" title={`Soil values are optional — ${entered} of ${SOIL_METRICS.length} entered`}>
        Enter only what you have. Blank values are saved as missing, and nothing is filled in or guessed for you.
      </Callout>

      <ChoiceGroup
        legend="Source of these values"
        columns={3}
        register={register('soilProfile.source')}
        error={e?.source?.message}
        options={SOIL_SOURCES.map((v) => ({
          value: v,
          label: soilSourceLabel[v].label,
          hint: soilSourceLabel[v].hint,
          badge: v === 'REGIONAL_ESTIMATE' ? <DataOriginBadge origin="estimate" label="Estimated" /> : undefined,
        }))}
      />
      {soil.source === 'REGIONAL_ESTIMATE' && (
        <Callout tone="caution" title="Estimated — not a measurement of this farm" className="-mt-4">
          A regional estimate describes typical soil in the surrounding area. It is not a laboratory measurement of this farm, and it will be labelled Estimated wherever it appears.
        </Callout>
      )}

      <ChoiceGroup
        legend="Data classification"
        columns={3}
        register={register('soilProfile.dataClassification')}
        error={e?.dataClassification?.message}
        options={SOIL_CLASSIFICATIONS.map((v) => ({ value: v, label: classificationLabel[v].label, hint: classificationLabel[v].hint }))}
      />
      {soil.dataClassification === 'SYNTHETIC' && (
        <Callout tone="synthetic" title="Synthetic — demo data" className="-mt-4">
          These values will be labelled synthetic everywhere and must not be used for real decisions.
        </Callout>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Measured on" optional error={e?.measuredAt?.message} hint="The sampling or report date.">
          {(a) => <Input {...a} type="date" max={new Date().toLocaleDateString('en-CA')} {...register('soilProfile.measuredAt')} />}
        </Field>
        <Field label="Confidence" suffix="0 to 1" optional error={e?.confidence?.message} hint="1 = a lab result you trust fully.">
          {(a) => <Input {...a} inputMode="decimal" autoComplete="off" placeholder="e.g. 1.0" className="tabular" {...register('soilProfile.confidence')} />}
        </Field>
      </div>

      {SOIL_GROUPS.map((group) => (
        <fieldset key={group.label} className="flex flex-col gap-3 border-t pt-5">
          <legend className="float-left mb-3 w-full text-sm font-semibold">{group.label}</legend>
          <div className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-3">
            {group.metrics.map((m) => (
              <Field key={m.key} label={m.label} suffix={m.unit || undefined} error={e?.[m.key]?.message}>
                {(a) => <Input {...a} inputMode="decimal" autoComplete="off" placeholder="—" className="tabular" {...register(`soilProfile.${m.key}`)} />}
              </Field>
            ))}
          </div>
        </fieldset>
      ))}
    </div>
  )
}
