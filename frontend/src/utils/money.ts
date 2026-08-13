export function formatMoney(amount: number | string | null | undefined, currency = 'USD'): string {
  if (amount == null || amount === '') return '—'
  const n = typeof amount === 'number' ? amount : Number(amount)
  if (Number.isNaN(n)) return '—'
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(n)
  } catch {
    return `${n.toFixed(2)} ${currency}`
  }
}
