import { createBrowserRouter } from 'react-router'
import { ModulePage, NotFoundPage } from '@/app/ModulePage'
import { navigation } from '@/app/navigation'
import { AppShell } from '@/components/layout/AppShell'
import { DashboardPage } from '@/features/dashboard/DashboardPage'

const modules = navigation.flatMap((g) => g.items).filter((item) => item.path !== '/')

export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <DashboardPage /> },
      ...modules.map((item) => ({ path: item.path, element: <ModulePage item={item} /> })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
