/** First letter of the first two words, e.g. "Laura Gómez" -> "LG". */
export function initials(name: string): string {
  const letters = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? '')
    .join('')
  return letters.toUpperCase() || '?'
}
