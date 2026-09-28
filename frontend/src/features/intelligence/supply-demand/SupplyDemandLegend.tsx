/** Legend shared by every supply-vs-demand chart: colour = series, dash = forecast. */
export function SupplyDemandLegend() {
  const item = (label: string, color: string, dashed?: boolean) => (
    <li className="flex items-center gap-1.5">
      <svg width="18" height="6" aria-hidden>
        <line x1="0" y1="3" x2="18" y2="3" stroke={color} strokeWidth="2" strokeDasharray={dashed ? '4 3' : undefined} />
      </svg>
      {label}
    </li>
  )
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-label="Legend">
      {item('Supply', 'var(--supply)')}
      {item('Demand', 'var(--demand)')}
      {item('Forecast (dashed)', 'var(--muted-foreground)', true)}
    </ul>
  )
}
