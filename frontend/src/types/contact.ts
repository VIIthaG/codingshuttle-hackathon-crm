export interface Contact {
  id: string
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  jobTitle: string | null
  notes: string | null
  accountId: string | null
  accountName: string | null
  ownerId: string
  ownerName: string
  createdAt: string
  updatedAt: string
}

export interface ContactCreateRequest {
  firstName: string
  lastName: string
  email?: string | null
  phone?: string | null
  jobTitle?: string | null
  notes?: string | null
  accountId?: string | null
  ownerId?: string | null
}

export interface ContactUpdateRequest {
  firstName: string
  lastName: string
  email?: string | null
  phone?: string | null
  jobTitle?: string | null
  notes?: string | null
  accountId?: string | null
  ownerId: string
}
