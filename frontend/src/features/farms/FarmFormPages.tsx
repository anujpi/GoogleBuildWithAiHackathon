import { useNavigate, useParams } from 'react-router'
import { AsyncContent } from '@/components/feedback/states'
import { PageHeader } from '@/components/layout/PageHeader'
import { describeError } from '@/lib/api/client'
import { useCreateFarm, useFarm, useUpdateFarm } from './hooks'
import { emptyFarmForm, farmToForm } from './schema'
import { FarmWizard } from './wizard/FarmWizard'

/** Passed through navigation so the profile page can confirm the save. */
export type SavedState = { saved?: 'created' | 'updated' }

export function NewFarmPage() {
  const navigate = useNavigate()
  const create = useCreateFarm()

  return (
    <div className="flex flex-col gap-6">
      <title>Add farm · Agri Intelligence</title>
      <PageHeader title="Add a farm" description="Four short steps. Nothing is saved until you choose Create Farm on the review step." />
      <FarmWizard
        mode="create"
        defaultValues={emptyFarmForm}
        cancelTo="/farms"
        onSave={async (body) => {
          const farm = await create.mutateAsync(body)
          navigate(`/farms/${farm.id}`, { state: { saved: 'created' } satisfies SavedState })
        }}
      />
    </div>
  )
}

export function EditFarmPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const farm = useFarm(id)
  const update = useUpdateFarm(id)

  return (
    <div className="flex flex-col gap-6">
      <title>{`Edit ${farm.data?.name ?? 'farm'} · Agri Intelligence`}</title>
      <PageHeader title={farm.data ? `Edit ${farm.data.name}` : 'Edit farm'} description="Changes are saved only when you choose Save changes on the review step." />
      <AsyncContent query={farm} loadingMessage="Loading farm..." errorMessage={describeError(farm.error, 'this farm')}>
        {(data) => (
          <FarmWizard
            mode="edit"
            defaultValues={farmToForm(data)}
            cancelTo={`/farms/${id}`}
            onSave={async (body) => {
              await update.mutateAsync(body)
              navigate(`/farms/${id}`, { state: { saved: 'updated' } satisfies SavedState })
            }}
          />
        )}
      </AsyncContent>
    </div>
  )
}
