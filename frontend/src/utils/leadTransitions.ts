import type { LeadSource, LeadStatus } from '../types/lead'

/** Mirrors backend LeadStatusTransitions. */
const ALLOWED: Record<LeadStatus, LeadStatus[]> = {
  NEW: ['CONTACTED', 'LOST'],
  CONTACTED: ['QUALIFIED', 'LOST'],
  QUALIFIED: ['LOST'],
  CONVERTED: [],
  LOST: [],
}

export const LEAD_STATUS_ORDER: LeadStatus[] = [
  'NEW',
  'CONTACTED',
  'QUALIFIED',
  'CONVERTED',
  'LOST',
]

export const LEAD_SOURCES: LeadSource[] = ['WEB', 'REFERRAL', 'COLD_CALL', 'EVENT', 'OTHER']

export function allowedLeadTransitions(from: LeadStatus): LeadStatus[] {
  return ALLOWED[from] ?? []
}

export function isTerminalLeadStatus(status: LeadStatus): boolean {
  return allowedLeadTransitions(status).length === 0
}

export function formatLeadSource(source: LeadSource): string {
  switch (source) {
    case 'COLD_CALL':
      return 'Cold call'
    case 'WEB':
      return 'Web'
    case 'REFERRAL':
      return 'Referral'
    case 'EVENT':
      return 'Event'
    case 'OTHER':
      return 'Other'
    default:
      return source
  }
}
