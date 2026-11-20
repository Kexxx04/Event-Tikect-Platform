import { ApiError, errorMessage } from '../api/client'
import { Alert } from './Alert'

interface ErrorAlertProps {
  error: unknown
  /** Fields already rendered next to their inputs, so they are not repeated here. */
  fieldsShownInline?: readonly string[]
}

export function ErrorAlert({ error, fieldsShownInline = [] }: ErrorAlertProps) {
  if (!error) {
    return null
  }

  const otherDetails =
    error instanceof ApiError
      ? Object.entries(error.details).filter(([field]) => !fieldsShownInline.includes(field))
      : []

  return (
    <Alert tone="error" title={errorMessage(error)}>
      {otherDetails.length > 0 && (
        <ul>
          {otherDetails.map(([field, message]) => (
            <li key={field}>
              <strong>{field}</strong>: {message}
            </li>
          ))}
        </ul>
      )}
    </Alert>
  )
}
