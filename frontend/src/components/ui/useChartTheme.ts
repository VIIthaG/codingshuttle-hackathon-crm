// import { useMemo } from 'react'
// import { useTheme } from '../../theme/useTheme'

// export function useChartTheme() {
//   const { theme } = useTheme()
//   return useMemo(() => {
//     const styles = getComputedStyle(document.documentElement)
//     const read = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback
//     return {
//       grid: read('--chart-grid', '#e2e8f0'),
//       tick: read('--chart-tick', '#64748b'),
//       tooltipBg: read('--chart-tooltip-bg', '#ffffff'),
//       tooltipBorder: read('--chart-tooltip-border', '#e2e8f0'),
//       ink: read('--app-ink', '#0f172a'),
//       brand: '#2563eb',
//       brandSoft: read('--app-brand-100', '#d9ebff'),
//       success: '#059669',
//       warning: '#d97706',
//     }
//   }, [theme])
// }
import { useTheme } from '../../theme/useTheme'

export function useChartTheme() {
  useTheme()

  const styles = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) =>
    styles.getPropertyValue(name).trim() || fallback

  return {
    grid: read('--chart-grid', '#e2e8f0'),
    tick: read('--chart-tick', '#64748b'),
    tooltipBg: read('--chart-tooltip-bg', '#ffffff'),
    tooltipBorder: read('--chart-tooltip-border', '#e2e8f0'),
    ink: read('--app-ink', '#0f172a'),
    brand: '#2563eb',
    brandSoft: read('--app-brand-100', '#d9ebff'),
    success: '#059669',
    warning: '#d97706',
  }
}