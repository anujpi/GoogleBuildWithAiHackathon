import { MapPin, MapPinOff, X } from 'lucide-react'
import { useFormContext, useWatch } from 'react-hook-form'
import { Field } from '@/components/forms/Field'
import { LocationPicker, type LatLon } from '@/components/maps/LocationPicker'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatCoords } from '../model'
import type { FarmFormValues } from '../schema'

/** A coordinate pair only counts as "selected" when both parts are valid numbers in range. */
function toPoint(lat: string, lon: string): LatLon | null {
  if (lat.trim() === '' || lon.trim() === '') return null
  const a = Number(lat)
  const b = Number(lon)
  return Number.isFinite(a) && Number.isFinite(b) && Math.abs(a) <= 90 && Math.abs(b) <= 180 ? { lat: a, lon: b } : null
}

export function LocationStep() {
  const { register, setValue, formState: { errors } } = useFormContext<FarmFormValues>()
  const [lat, lon] = useWatch<FarmFormValues, ['location.latitude', 'location.longitude']>({ name: ['location.latitude', 'location.longitude'] })
  const point = toPoint(lat, lon)
  const e = errors.location

  const setPoint = (p: { lat: string; lon: string }) => {
    const opts = { shouldDirty: true, shouldValidate: p.lat !== '' }
    setValue('location.latitude', p.lat, opts)
    setValue('location.longitude', p.lon, opts)
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      <LocationPicker
        label="Farm location map. Click to set coordinates."
        value={point}
        onPick={(p) => setPoint({ lat: String(p.lat), lon: String(p.lon) })}
        className="h-72 rounded-md border sm:h-80 lg:h-full lg:min-h-[26rem]"
      />

      <div className="flex flex-col gap-5">
        <fieldset className="flex flex-col gap-3">
          <legend className="mb-1 text-sm font-semibold">Coordinates</legend>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Latitude" suffix="−90 to 90" error={e?.latitude?.message}>
              {(a) => <Input {...a} inputMode="decimal" autoComplete="off" placeholder="e.g. 12.7200" className="tabular" {...register('location.latitude')} />}
            </Field>
            <Field label="Longitude" suffix="−180 to 180" error={e?.longitude?.message}>
              {(a) => <Input {...a} inputMode="decimal" autoComplete="off" placeholder="e.g. 77.2800" className="tabular" {...register('location.longitude')} />}
            </Field>
          </div>

          <div role="status" aria-live="polite" className="flex items-center gap-2 rounded-md border bg-muted/50 px-3 py-2 text-sm">
            {point ? (
              <>
                <MapPin className="size-4 shrink-0 text-primary" aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="text-muted-foreground">Selected </span>
                  <span className="tabular font-medium">{formatCoords(point.lat, point.lon)}</span>
                </span>
                <Button type="button" variant="ghost" size="sm" onClick={() => setPoint({ lat: '', lon: '' })}>
                  <X aria-hidden /> Clear
                </Button>
              </>
            ) : (
              <>
                <MapPinOff className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                <span className="text-muted-foreground">No location selected. Click the map or enter coordinates.</span>
              </>
            )}
          </div>
        </fieldset>

        <fieldset className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <legend className="mb-1 text-sm font-semibold">Administrative area</legend>
          <Field label="State" error={e?.state?.message}>
            {(a) => <Input {...a} autoComplete="address-level1" placeholder="e.g. Karnataka" {...register('location.state')} />}
          </Field>
          <Field label="District" error={e?.district?.message}>
            {(a) => <Input {...a} autoComplete="address-level2" placeholder="e.g. Ramanagara" {...register('location.district')} />}
          </Field>
          <Field label="Taluk" optional error={e?.taluk?.message}>
            {(a) => <Input {...a} placeholder="e.g. Ramanagara" {...register('location.taluk')} />}
          </Field>
          <Field label="Address label" optional error={e?.addressLabel?.message} hint="How the farm is named in lists.">
            {(a) => <Input {...a} placeholder="e.g. Ramanagara, Karnataka" {...register('location.addressLabel')} />}
          </Field>
        </fieldset>
      </div>
    </div>
  )
}
