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
import { register as postRegister } from './api'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './context'

const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter your full name').max(200, 'Use 200 characters or fewer'),
    email: z.string().trim().min(1, 'Enter your email address').pipe(z.email('Enter a valid email address')),
    password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use 72 characters or fewer'),
    confirmPassword: z.string().min(1, 'Re-enter your password'),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })
type Values = z.infer<typeof schema>

const serverFields = ['fullName', 'email', 'password'] as const
const isServerField = (f: string): f is (typeof serverFields)[number] => (serverFields as readonly string[]).includes(f)

export function RegisterPage() {
  const { login } = useAuth()
  const [failure, setFailure] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', confirmPassword: '' },
  })

  const submit = handleSubmit(async ({ fullName, email, password }) => {
    setFailure(null)
    try {
      await postRegister({ fullName, email, password })
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || /DUPLICATE|EXISTS|TAKEN/.test(error.code))) {
        setError('email', { type: 'server', message: 'An account with this email already exists. Sign in instead.' }, { shouldFocus: true })
        return
      }
      const unplaced = error instanceof ApiError && (error.status === 400 || error.status === 422) ? error.details.filter((d) => !isServerField(d.field)) : []
      if (error instanceof ApiError) for (const d of error.details) if (isServerField(d.field)) setError(d.field, { type: 'server', message: d.message })
      setFailure(unplaced.length ? unplaced.map((d) => d.message).join(' ') : describeError(error, 'your account'))
      return
    }
    // The account exists now; sign straight in. PublicOnly then redirects to the dashboard.
    try {
      await login({ email, password })
    } catch (error) {
      setFailure(`Your account was created, but signing in failed. ${describeError(error, 'sign-in')}`)
    }
  })

  return (
    <AuthLayout title="Create an account" description="New accounts are farmer accounts. You can add farms after signing in.">
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        {failure && <Callout tone="critical" role="alert" title="Could not create the account">{failure}</Callout>}
        <Field label="Full name" error={errors.fullName?.message}>
          {(a) => <Input {...a} autoComplete="name" autoFocus {...register('fullName')} />}
        </Field>
        <Field label="Email" error={errors.email?.message}>
          {(a) => <Input {...a} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label="Password" error={errors.password?.message} hint="At least 8 characters.">
          {(a) => <Input {...a} type="password" autoComplete="new-password" {...register('password')} />}
        </Field>
        <Field label="Confirm password" error={errors.confirmPassword?.message}>
          {(a) => <Input {...a} type="password" autoComplete="new-password" {...register('confirmPassword')} />}
        </Field>
        <Button type="submit" disabled={isSubmitting} className="mt-2">
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </Button>
      </form>
      <p className="mt-6 border-t pt-4 text-sm text-muted-foreground">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-foreground underline underline-offset-4 hover:text-primary">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
