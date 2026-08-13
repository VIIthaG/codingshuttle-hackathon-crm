import type { DealStage } from '../types/deal'

const ALLOWED: Record<DealStage, DealStage[]> = {
  PROSPECTING: ['QUALIFICATION', 'CLOSED_LOST'],
  QUALIFICATION: ['PROPOSAL', 'CLOSED_LOST'],
  PROPOSAL: ['NEGOTIATION', 'CLOSED_LOST'],
  NEGOTIATION: ['CLOSED_WON', 'CLOSED_LOST'],
  CLOSED_WON: [],
  CLOSED_LOST: [],
}

export const DEAL_STAGE_ORDER: DealStage[] = [
  'PROSPECTING',
  'QUALIFICATION',
  'PROPOSAL',
  'NEGOTIATION',
  'CLOSED_WON',
  'CLOSED_LOST',
]

export const DEAL_STAGE_LABELS: Record<DealStage, string> = {
  PROSPECTING: 'Prospecting',
  QUALIFICATION: 'Qualification',
  PROPOSAL: 'Proposal',
  NEGOTIATION: 'Negotiation',
  CLOSED_WON: 'Closed Won',
  CLOSED_LOST: 'Closed Lost',
}

export function allowedDealTransitions(from: DealStage): DealStage[] {
  return ALLOWED[from] ?? []
}

export function isTerminalDealStage(stage: DealStage): boolean {
  return allowedDealTransitions(stage).length === 0
}

export function formatDealStage(stage: DealStage): string {
  return DEAL_STAGE_LABELS[stage] ?? stage
}
