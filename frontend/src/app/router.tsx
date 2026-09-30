import { createBrowserRouter, Navigate } from 'react-router'
import { NotFoundPage } from '@/app/ModulePage'
import { AppShell } from '@/components/layout/AppShell'
import { AuthProvider, FarmOwnersOnly, PublicOnly, RequireAuth } from '@/features/auth/AuthProvider'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { EditFarmPage, NewFarmPage } from '@/features/farms/FarmFormPages'
import { FarmProfilePage } from '@/features/farms/FarmProfilePage'
import { FarmsPage } from '@/features/farms/FarmsPage'
import { DashboardPage } from '@/features/insight/DashboardPage'
import { SupplyPage } from '@/features/insight/SupplyPage'

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
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/supply', element: <SupplyPage /> },
              {
                element: <FarmOwnersOnly />,
                children: [
                  { path: '/farms', element: <FarmsPage /> },
                  { path: '/farms/new', element: <NewFarmPage /> },
                  { path: '/farms/:id', element: <FarmProfilePage /> },
                  { path: '/farms/:id/edit', element: <EditFarmPage /> },
                ],
              },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
])
