import { AlertCircleIcon } from 'lucide-react'

import { ApiRequestError } from '@/api/client'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export function errorMessage(error: unknown): { title: string; details: string[] } {
  if (error instanceof ApiRequestError) {
    return { title: error.message, details: error.details }
  }
  if (error instanceof Error) {
    return { title: error.message, details: [] }
  }
  return { title: 'Something went wrong', details: [] }
}

export function ErrorAlert({ error }: { error: unknown }) {
  const { title, details } = errorMessage(error)
  return (
    <Alert variant="destructive">
      <AlertCircleIcon />
      <AlertTitle>{title}</AlertTitle>
      {details.length > 0 && (
        <AlertDescription>
          <ul className="list-disc pl-4">
            {details.map((detail) => (
              <li key={detail}>{detail}</li>
            ))}
          </ul>
        </AlertDescription>
      )}
    </Alert>
  )
}
