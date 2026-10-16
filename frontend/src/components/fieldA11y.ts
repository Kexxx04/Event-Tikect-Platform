/** ARIA attributes that link a form control to the hint or error rendered by <FormField>. */
export function fieldA11y(id: string, error?: string, hint?: string) {
  return {
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${id}-error` : hint ? `${id}-hint` : undefined,
  }
}
