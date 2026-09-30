import { Link } from 'react-router'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'

function Message({ title, heading, description }: { title: string; heading: string; description: string }) {
  return (
    <div className="flex flex-col gap-6">
      <title>{`${title} · Agri Intelligence`}</title>
      <PageHeader title={heading} description={description} />
      <div>
        <Button asChild variant="outline" size="sm">
          <Link to="/dashboard">Back to dashboard</Link>
        </Button>
      </div>
    </div>
  )
}

export const NotFoundPage = () => <Message title="Not found" heading="Page not found" description="This address does not match any page." />

export const ForbiddenPage = () => (
  <Message title="Forbidden" heading="Not available for your role" description="Your account's role does not have access to this page." />
)
