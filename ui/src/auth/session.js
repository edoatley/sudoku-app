// Cloud-agnostic auth session accessors. The active identity provider is chosen at build time by
// VITE_AUTH_PROVIDER (default 'cognito' for the AWS build; 'firebase' for the GCP build). Each
// provider's SDK is imported lazily so the other cloud's SDK is never bundled.
// @spec CP-GCP-042
export const AUTH_PROVIDER = import.meta.env.VITE_AUTH_PROVIDER || 'cognito';

const isFirebase = AUTH_PROVIDER === 'firebase';

/** The bearer ID token for the current session, or undefined if signed out. */
export async function getIdToken() {
  if (isFirebase) {
    const { firebaseGetIdToken } = await import('./firebase.js');
    return firebaseGetIdToken();
  }
  const { fetchAuthSession } = await import('aws-amplify/auth');
  const session = await fetchAuthSession();
  return session.tokens?.idToken?.toString();
}

/** The signed-in user's email, or null. */
export async function getEmail() {
  if (isFirebase) {
    const { firebaseGetEmail } = await import('./firebase.js');
    return firebaseGetEmail();
  }
  const { fetchAuthSession } = await import('aws-amplify/auth');
  const session = await fetchAuthSession();
  return session.tokens?.idToken?.payload?.email ?? null;
}

/**
 * Group claims for admin gating. Cognito-only — Identity Platform has no group concept and admin
 * authz on GCP is deferred (UM-GCP-008), so the Firebase path reports no groups.
 */
export async function getAdminGroups() {
  if (isFirebase) {
    return [];
  }
  const { fetchAuthSession } = await import('aws-amplify/auth');
  const session = await fetchAuthSession();
  const groups = session.tokens?.idToken?.payload?.['cognito:groups'];
  return Array.isArray(groups) ? groups : [];
}
