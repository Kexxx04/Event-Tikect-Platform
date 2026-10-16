import { useCallback, useEffect, useState } from 'react'

interface LoaderState<T> {
  data: T | undefined
  error: unknown
  loading: boolean
}

/**
 * Runs an async loader when the dependencies change and exposes loading / error / data.
 * Responses that arrive after the component re-rendered with new dependencies are ignored.
 */
export function useLoader<T>(load: () => Promise<T>, deps: readonly unknown[]) {
  const [state, setState] = useState<LoaderState<T>>({ data: undefined, error: null, loading: true })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    setState((previous) => ({ ...previous, error: null, loading: true }))
    load()
      .then((data) => {
        if (active) setState({ data, error: null, loading: false })
      })
      .catch((error: unknown) => {
        if (active) setState({ data: undefined, error, loading: false })
      })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version])

  const reload = useCallback(() => setVersion((value) => value + 1), [])
  const setData = useCallback((data: T) => setState({ data, error: null, loading: false }), [])

  return { ...state, reload, setData }
}
