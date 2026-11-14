/** Controllable `prefers-color-scheme` for tests (jsdom has no matchMedia). */
type Listener = (e: { matches: boolean }) => void;
const listeners = new Set<Listener>();
let prefersDark = false;

export function installMatchMedia() {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string) => ({
      get matches() {
        return query.includes('dark') ? prefersDark : false;
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, l: Listener) => listeners.add(l),
      removeEventListener: (_: string, l: Listener) => listeners.delete(l),
      addListener: (l: Listener) => listeners.add(l),
      removeListener: (l: Listener) => listeners.delete(l),
      dispatchEvent: () => true,
    }),
  });
}

/** Simulates the OS switching between light and dark. */
export function setSystemDark(dark: boolean) {
  prefersDark = dark;
  listeners.forEach((l) => l({ matches: dark }));
}
