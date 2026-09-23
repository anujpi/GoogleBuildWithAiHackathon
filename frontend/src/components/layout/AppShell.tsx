import { FlaskConical, Menu } from 'lucide-react'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { navigation } from '@/app/navigation'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ProductMark, Sidebar, SidebarNav } from './Sidebar'

function useCurrentModule() {
  const { pathname } = useLocation()
  for (const group of navigation)
    for (const item of group.items)
      if (item.path === '/' ? pathname === '/' : pathname.startsWith(item.path)) return { group: group.label, item }
  return null
}

function TopBar() {
  const [open, setOpen] = useState(false)
  const current = useCurrentModule()

  return (
    <header className="sticky top-0 z-20 flex h-12 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80 md:px-6">
      <Sheet open={open} onOpenChange={setOpen}>
        <Button variant="ghost" size="icon" className="-ml-2 lg:hidden" onClick={() => setOpen(true)} aria-label="Open navigation">
          <Menu />
        </Button>
        <SheetContent side="left" className="w-72 gap-6 border-sidebar-border bg-sidebar p-3 text-sidebar-foreground">
          <SheetHeader className="p-1">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            <SheetDescription className="sr-only">Move between platform modules</SheetDescription>
            <ProductMark />
          </SheetHeader>
          <SidebarNav onNavigate={() => setOpen(false)} />
        </SheetContent>
      </Sheet>

      <nav aria-label="Breadcrumb" className="min-w-0 text-sm">
        <ol className="flex items-center gap-1.5">
          {current && <li className="hidden text-muted-foreground sm:block">{current.group}</li>}
          {current && <li aria-hidden className="hidden text-muted-foreground/50 sm:block">/</li>}
          <li aria-current="page" className="truncate font-medium">{current?.item.label ?? 'Not found'}</li>
        </ol>
      </nav>

      <div className="ml-auto flex items-center gap-2">
        <span className="inline-flex h-6 items-center gap-1.5 rounded-sm border border-caution/30 bg-caution/12 px-2 text-xs font-medium text-caution">
          <FlaskConical className="size-3.5" aria-hidden />
          <span className="hidden sm:inline">Prototype ·</span> Synthetic data
        </span>
      </div>
    </header>
  )
}

export function AppShell() {
  return (
    <TooltipProvider delayDuration={300}>
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-sm focus:bg-primary focus:px-3 focus:py-2 focus:text-sm focus:text-primary-foreground"
      >
        Skip to content
      </a>
      <div className="flex min-h-svh">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar />
          <main id="main" tabIndex={-1} className="flex-1 px-4 py-5 outline-none md:px-6 md:py-6">
            <Outlet />
          </main>
        </div>
      </div>
    </TooltipProvider>
  )
}
