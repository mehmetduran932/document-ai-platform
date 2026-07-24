import { Badge } from '@/components/ui/badge'
import type { ProcessingStatus } from '@/api/types'

const variantByStatus: Record<ProcessingStatus, 'secondary' | 'warning' | 'success' | 'destructive'> = {
  PENDING: 'secondary',
  PROCESSING: 'warning',
  COMPLETED: 'success',
  FAILED: 'destructive',
}

export function StatusBadge({ status }: { status: ProcessingStatus }) {
  return <Badge variant={variantByStatus[status]}>{status}</Badge>
}
