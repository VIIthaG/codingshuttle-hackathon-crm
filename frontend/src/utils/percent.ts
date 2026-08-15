export function formatPercent(rate: number | string | null | undefined): string {
  if (rate == null || rate === '') return '0%'
  const n = typeof rate === 'number' ? rate : Number(rate)
  if (!Number.isFinite(n)) return '0%'
  return `${(n * 100).toFixed(n === 0 || n === 1 ? 0 : 1)}%`
}

export function asNumber(value: number | string | null | undefined): number {
  if (value == null || value === '') return 0
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : 0
}
