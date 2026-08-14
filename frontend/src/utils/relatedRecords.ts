import type { RelatedRecordType } from '../types/task'

export function applyRelatedIds<T extends Record<string, unknown>>(
  body: T,
  type: RelatedRecordType,
  id: string,
): T {
  const next = {
    ...body,
    leadId: null,
    accountId: null,
    contactId: null,
    dealId: null,
  } as T & {
    leadId: string | null
    accountId: string | null
    contactId: string | null
    dealId: string | null
  }
  if (type === 'LEAD') next.leadId = id
  if (type === 'ACCOUNT') next.accountId = id
  if (type === 'CONTACT') next.contactId = id
  if (type === 'DEAL') next.dealId = id
  return next
}
