export type LeadSource = 'WEB' | 'REFERRAL' | 'COLD_CALL' | 'EVENT' | 'OTHER'

export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'LOST' | 'CONVERTED'

/** Matches backend LeadResponse */
export interface Lead {
  id: string
  fullName: string
  email: string | null
  phone: string | null
  company: string | null
  source: LeadSource
  status: LeadStatus
  assignedToId: string
  assignedToName: string
  createdAt: string
  updatedAt: string
}

/** Matches backend LeadCreateRequest */
export interface LeadCreateRequest {
  fullName: string
  email?: string | null
  phone?: string | null
  company?: string | null
  source: LeadSource
  status?: LeadStatus | null
  assignedToId?: string | null
}

/** Matches backend LeadUpdateRequest */
export interface LeadUpdateRequest {
  fullName: string
  email?: string | null
  phone?: string | null
  company?: string | null
  source: LeadSource
  status: LeadStatus
  assignedToId: string
}

/** Matches backend LeadStatusUpdateRequest */
export interface LeadStatusUpdateRequest {
  status: LeadStatus
}
