import { ApiError, errorMessage } from '../api/client'

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
    <div className="alert alert-error" role="alert">
      <p className="alert-title">
        {error instanceof ApiError && error.status > 0 ? `Error ${error.status}: ` : ''}
        {errorMessage(error)}
      </p>
      {otherDetails.length > 0 && (
        <ul>
          {otherDetails.map(([field, message]) => (
            <li key={field}>
              <strong>{field}</strong>: {message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
