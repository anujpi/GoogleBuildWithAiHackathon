import { useFormContext } from 'react-hook-form'
import { ChoiceGroup } from '@/components/forms/ChoiceGroup'
import { Field } from '@/components/forms/Field'
import { Input } from '@/components/ui/input'
import { AREA_UNITS, areaUnitLabel, IRRIGATION_TYPES, irrigationLabel, SEASONS, seasonLabel } from '../model'
import type { FarmFormValues } from '../schema'

export function FarmInfoStep() {
  const { register, formState: { errors: e } } = useFormContext<FarmFormValues>()

  return (
    <div className="flex flex-col gap-7">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <Field label="Farm name" error={e.name?.message}>
          {(a) => <Input {...a} autoComplete="off" placeholder="e.g. Green Valley Farm" {...register('name')} />}
        </Field>
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3">
          <Field label="Area" error={e.area?.message}>
            {(a) => <Input {...a} inputMode="decimal" autoComplete="off" placeholder="e.g. 12.5" className="tabular" {...register('area')} />}
          </Field>
          <fieldset className="flex flex-col gap-1.5" aria-describedby={e.areaUnit ? 'area-unit-error' : undefined}>
            <legend className="mb-1.5 text-sm font-medium">Unit</legend>
            {/* Compact segmented control; native radios keep arrow-key behaviour. */}
            <div className="flex h-8 rounded-md border bg-muted p-0.5">
              {AREA_UNITS.map((u) => (
                <label key={u} className="relative flex cursor-pointer items-center rounded-sm px-3 text-sm text-muted-foreground has-[:checked]:bg-card has-[:checked]:font-medium has-[:checked]:text-foreground has-[:checked]:shadow-sm has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring">
                  <input type="radio" value={u} className="sr-only" {...register('areaUnit')} />
                  {areaUnitLabel[u].long}
                </label>
              ))}
            </div>
            {e.areaUnit && (
              <p id="area-unit-error" className="text-xs font-medium text-destructive">
                {e.areaUnit.message}
              </p>
            )}
          </fieldset>
        </div>
      </div>

      <ChoiceGroup
        legend="Irrigation"
        columns={3}
        register={register('irrigationType')}
        error={e.irrigationType?.message}
        options={IRRIGATION_TYPES.map((v) => ({ value: v, label: irrigationLabel[v] }))}
      />

      <ChoiceGroup
        legend="Season"
        columns={4}
        register={register('season')}
        error={e.season?.message}
        options={SEASONS.map((v) => ({ value: v, label: seasonLabel[v].label, hint: seasonLabel[v].hint }))}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Current crop" optional error={e.currentCrop?.message} hint="Free text for now, e.g. Tomato.">
          {(a) => <Input {...a} autoComplete="off" placeholder="e.g. Tomato" {...register('currentCrop')} />}
        </Field>
        <Field label="Previous crop" optional error={e.previousCrop?.message} hint="The crop grown in the last season.">
          {(a) => <Input {...a} autoComplete="off" placeholder="e.g. Millet" {...register('previousCrop')} />}
        </Field>
      </div>
    </div>
  )
}
