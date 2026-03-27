// Vitest 3 + jsdom 26 does not expose localStorage methods on the global.
// Install a simple in-memory Storage implementation so tests can use
// localStorage.getItem / setItem / removeItem / clear as normal.

function makeStorage() {
  let store = {};
  return {
    getItem: (key) => (key in store ? store[key] : null),
    setItem: (key, value) => { store[key] = String(value); },
    removeItem: (key) => { delete store[key]; },
    clear: () => { store = {}; },
    key: (i) => Object.keys(store)[i] ?? null,
    get length() { return Object.keys(store).length; },
  };
}

Object.defineProperty(globalThis, 'localStorage', {
  value: makeStorage(),
  writable: true,
});

Object.defineProperty(globalThis, 'sessionStorage', {
  value: makeStorage(),
  writable: true,
});
