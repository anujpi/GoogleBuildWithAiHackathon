import type { ReactNode } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import { ModulePage, NotFoundPage } from '@/app/ModulePage'
import { navigation } from '@/app/navigation'
import { AppShell } from '@/components/layout/AppShell'
import { AuthProvider, PublicOnly, RequireAuth } from '@/features/auth/AuthProvider'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { EditFarmPage, NewFarmPage } from '@/features/farms/FarmFormPages'
import { FarmProfilePage } from '@/features/farms/FarmProfilePage'
import { FarmsPage } from '@/features/farms/FarmsPage'

// Modules with real screens. Every other navigation entry gets its "scheduled for phase N" page.
const built: Record<string, { path: string; element: ReactNode }[]> = {
  '/dashboard': [{ path: '/dashboard', element: <DashboardPage /> }],
  '/farms': [
    { path: '/farms', element: <FarmsPage /> },
    { path: '/farms/new', element: <NewFarmPage /> },
    { path: '/farms/:id', element: <FarmProfilePage /> },
    { path: '/farms/:id/edit', element: <EditFarmPage /> },
  ],
}

const moduleRoutes = navigation
  .flatMap((g) => g.items)
  .flatMap((item) => built[item.path] ?? [{ path: item.path, element: <ModulePage item={item} /> }])

export const router = createBrowserRouter([
  {
    element: <AuthProvider />,
    children: [
      {
        element: <PublicOnly />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Navigate to="/dashboard" replace /> },
              ...moduleRoutes,
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
])
