import { FlaskConical } from 'lucide-react'
import { NavLink } from 'react-router'
import { cn } from 'cn'
import { navigation } from '@/app/navigation'

export function ProductMark() {
  return (
    <div className="flex items-center gap-2.5">
      <svg viewBox="0 0 32 32" className="size-7 shrink-0" aria-hidden>
        <rect width="32" height="32" rx="6" className="fill-sidebar-accent" />
        <path d="M8 22h16M8 17h11M8 12h6" className="stroke-sidebar-primary" strokeWidth="2.5" strokeLinecap="round" />
      </svg>
      <div className="leading-tight">
        <div className="text-sm font-semibold text-sidebar-accent-foreground">Agri Intelligence</div>
        <div className="text-[11px] text-sidebar-foreground/70">Decision command center</div>
      </div>
    </div>
  )
}

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Main" className="flex flex-col gap-5">
      {navigation.map((group) => (
        <div key={group.label}>
          <div className="px-3 pb-1.5 text-[11px] font-medium tracking-wider text-sidebar-foreground/55 uppercase">
            {group.label}
          </div>
          <ul className="flex flex-col gap-px">
            {group.items.map(({ path, label, icon: Icon }) => (
              <li key={path}>
                <NavLink
                  to={path}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'relative flex h-8 items-center gap-2.5 rounded-sm px-3 text-sm outline-none transition-colors',
                      'hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                      'focus-visible:ring-2 focus-visible:ring-sidebar-ring',
                      isActive &&
                        'bg-sidebar-accent font-medium text-sidebar-accent-foreground before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-sidebar-primary',
                    )
                  }
                >
                  <Icon className="size-4 shrink-0 opacity-80" aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

export function Sidebar() {
  return (
    <aside className="sticky top-0 hidden h-svh w-60 shrink-0 flex-col gap-6 overflow-y-auto bg-sidebar p-3 text-sidebar-foreground lg:flex">
      <div className="px-1 pt-1">
        <ProductMark />
      </div>
      <SidebarNav />
      <div className="mt-auto flex items-start gap-2 rounded-sm border border-sidebar-border p-2.5 text-[11px] text-sidebar-foreground/80">
        <FlaskConical className="mt-px size-3.5 shrink-0 text-caution" aria-hidden />
        <p>Prototype build. Synthetic values are always labelled; farm records come from the backend.</p>
      </div>
    </aside>
  )
}
