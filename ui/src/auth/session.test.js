import { afterEach, describe, expect, it, vi } from 'vitest';

// session.js reads VITE_AUTH_PROVIDER at module load, so each case stubs the env, resets the module
// registry, then dynamically imports a fresh copy.
describe('auth/session provider dispatch', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.doUnmock('aws-amplify/auth');
    vi.doUnmock('./firebase.js');
    vi.resetModules();
  });

  it('cognito (default): reads token, email and groups from aws-amplify/auth', async () => {
    // Unset the provider so this genuinely exercises session.js's default-to-cognito fallback,
    // independent of .env.test's pin (which only guards against a stray .env.local firebase value).
    vi.stubEnv('VITE_AUTH_PROVIDER', undefined);
    vi.doMock('aws-amplify/auth', () => ({
      fetchAuthSession: vi.fn().mockResolvedValue({
        tokens: {
          idToken: {
            toString: () => 'cognito-token',
            payload: { email: 'a@b.com', 'cognito:groups': ['administrators'] },
          },
        },
      }),
    }));
    vi.resetModules();
    const session = await import('./session.js');

    expect(session.AUTH_PROVIDER).toBe('cognito');
    expect(await session.getIdToken()).toBe('cognito-token');
    expect(await session.getEmail()).toBe('a@b.com');
    expect(await session.getAdminGroups()).toEqual(['administrators']);
  });

  it('firebase: reads token/email from the firebase adapter and reports no groups', async () => {
    vi.stubEnv('VITE_AUTH_PROVIDER', 'firebase');
    vi.doMock('./firebase.js', () => ({
      firebaseGetIdToken: vi.fn().mockResolvedValue('firebase-token'),
      firebaseGetEmail: vi.fn().mockReturnValue('c@d.com'),
    }));
    vi.resetModules();
    const session = await import('./session.js');

    expect(session.AUTH_PROVIDER).toBe('firebase');
    expect(await session.getIdToken()).toBe('firebase-token');
    expect(await session.getEmail()).toBe('c@d.com');
    // Identity Platform has no group concept — admin authz on GCP is deferred (UM-GCP-008).
    expect(await session.getAdminGroups()).toEqual([]);
  });
});
