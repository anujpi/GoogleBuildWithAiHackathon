import { zodResolver } from '@hookform/resolvers/zod'
import { LoaderCircle } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { Callout } from '@/components/feedback/Callout'
import { Field } from '@/components/forms/Field'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError, describeError } from '@/lib/api/client'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './context'

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email address').pipe(z.email('Enter a valid email address')),
  password: z.string().min(1, 'Enter your password'),
})
type Values = z.infer<typeof schema>

export function LoginPage() {
  const { login, endedReason } = useAuth()
  const [failure, setFailure] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  })

  // Success needs no navigation here: PublicOnly redirects as soon as the session exists.
  const submit = handleSubmit(async (values) => {
    setFailure(null)
    try {
      await login(values)
    } catch (error) {
      setFailure(error instanceof ApiError && error.status === 401 ? 'Email or password is incorrect.' : describeError(error, 'sign-in'))
    }
  })

  return (
    <AuthLayout title="Sign in" description="Use the email and password you registered with.">
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        {failure ? (
          <Callout tone="critical" role="alert" title="Could not sign in">{failure}</Callout>
        ) : endedReason === 'expired' ? (
          <Callout tone="caution" role="status" title="Your session has ended">Sign in again to continue where you left off.</Callout>
        ) : endedReason === 'signed-out' ? (
          <Callout tone="info" role="status" title="You are signed out" />
        ) : null}
        <Field label="Email" error={errors.email?.message}>
          {(a) => <Input {...a} type="email" autoComplete="email" autoFocus {...register('email')} />}
        </Field>
        <Field label="Password" error={errors.password?.message}>
          {(a) => <Input {...a} type="password" autoComplete="current-password" {...register('password')} />}
        </Field>
        <Button type="submit" disabled={isSubmitting} className="mt-2">
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {isSubmitting ? 'Signing in...' : 'Sign in'}
        </Button>
      </form>
      <p className="mt-6 border-t pt-4 text-sm text-muted-foreground">
        New here?{' '}
        <Link to="/register" className="font-medium text-foreground underline underline-offset-4 hover:text-primary">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
