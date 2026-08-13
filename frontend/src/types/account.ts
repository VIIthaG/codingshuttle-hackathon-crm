export interface Account {
  id: string
  name: string
  website: string | null
  phone: string | null
  industry: string | null
  description: string | null
  ownerId: string
  ownerName: string
  contactCount: number
  createdAt: string
  updatedAt: string
}

export interface AccountCreateRequest {
  name: string
  website?: string | null
  phone?: string | null
  industry?: string | null
  description?: string | null
  ownerId?: string | null
}

export interface AccountUpdateRequest {
  name: string
  website?: string | null
  phone?: string | null
  industry?: string | null
  description?: string | null
  ownerId: string
}
