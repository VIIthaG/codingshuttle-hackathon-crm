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
  convertedAt?: string | null
  convertedAccountId?: string | null
  convertedAccountName?: string | null
  convertedContactId?: string | null
  convertedContactName?: string | null
  convertedDealId?: string | null
  convertedDealName?: string | null
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

/** Matches backend LeadConvertRequest */
export interface LeadConvertRequest {
  useExistingAccountId?: string | null
  accountName?: string | null
  accountWebsite?: string | null
  accountPhone?: string | null
  accountIndustry?: string | null
  useExistingContactId?: string | null
  contactFirstName?: string | null
  contactLastName?: string | null
  contactEmail?: string | null
  contactPhone?: string | null
  contactJobTitle?: string | null
  createDeal?: boolean
  dealName?: string | null
  amount?: number | null
  currency?: string | null
  expectedCloseDate?: string | null
  description?: string | null
}
