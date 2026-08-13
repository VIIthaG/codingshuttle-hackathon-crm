export type DealStage =
  | 'PROSPECTING'
  | 'QUALIFICATION'
  | 'PROPOSAL'
  | 'NEGOTIATION'
  | 'CLOSED_WON'
  | 'CLOSED_LOST'

export interface Deal {
  id: string
  name: string
  accountId: string
  accountName: string
  primaryContactId: string | null
  primaryContactName: string | null
  ownerId: string
  ownerName: string
  stage: DealStage
  amount: number | string | null
  currency: string
  probability: number
  expectedCloseDate: string | null
  description: string | null
  lostReason: string | null
  createdAt: string
  updatedAt: string
}

export interface DealCreateRequest {
  name: string
  accountId: string
  primaryContactId?: string | null
  ownerId?: string
  stage?: DealStage
  amount?: number | null
  currency?: string
  probability?: number
  expectedCloseDate?: string | null
  description?: string | null
  lostReason?: string | null
}

export interface DealUpdateRequest {
  name: string
  accountId: string
  primaryContactId?: string | null
  ownerId: string
  stage: DealStage
  amount?: number | null
  currency: string
  probability?: number
  expectedCloseDate?: string | null
  description?: string | null
  lostReason?: string | null
}

export interface DealStageUpdateRequest {
  stage: DealStage
  lostReason?: string | null
  probability?: number
}
