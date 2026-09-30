import { ArrowLeftRight, LayoutGrid, MapPinned, type LucideIcon } from 'lucide-react'
import type { UserRole } from '@/features/auth/api'

export type NavItem = {
  path: string
  label: string
  icon: LucideIcon
  /** Roles that see the entry. UX only: the backend is authoritative. Omitted = every role. */
  roles?: UserRole[]
}

export type NavGroup = { label: string; items: NavItem[] }

// Adding a module = adding an entry here and a route in router.tsx.
export const navigation: NavGroup[] = [
  {
    label: 'Intelligence',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutGrid },
      { path: '/farms', label: 'Farms', icon: MapPinned, roles: ['FARMER', 'FPO'] },
      { path: '/supply', label: 'Supply intelligence', icon: ArrowLeftRight },
    ],
  },
]
