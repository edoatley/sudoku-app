// Firebase (Identity Platform) auth adapter for the GCP deployment.
// Loaded lazily — only when VITE_AUTH_PROVIDER === 'firebase' — so the AWS/Cognito
// build never pulls in the Firebase SDK.
// @spec CP-GCP-030
import { initializeApp, getApps, getApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut as firebaseAuthSignOut,
  onAuthStateChanged,
} from 'firebase/auth';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

function auth() {
  const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
  return getAuth(app);
}

// Google is the sole interactive sign-in provider (no native sign-up in the app).
export async function firebaseSignInWithGoogle() {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  await signInWithPopup(auth(), provider);
}

export async function firebaseSignOut() {
  await firebaseAuthSignOut(auth());
}

export async function firebaseGetIdToken() {
  const a = auth();
  // Firebase restores the persisted session asynchronously; currentUser is null until it does.
  // Without this, calls made right after a page load/refresh send no Authorization header and 401.
  // getIdToken() auto-refreshes an expired token, so the returned token is always current.
  await a.authStateReady();
  const user = a.currentUser;
  // DIAGNOSTIC: shows whether the SDK has a signed-in user after the session is restored.
  console.info('[AUTH] authStateReady resolved; currentUser =', user ? user.email : 'NULL (no signed-in user)');
  if (!user) return undefined;
  const token = await user.getIdToken();
  console.info('[AUTH] getIdToken -> token length', token?.length ?? 0);
  return token;
}

export async function firebaseGetEmail() {
  const a = auth();
  await a.authStateReady();
  return a.currentUser?.email ?? null;
}

// Subscribe to auth state; returns the unsubscribe function.
export function onFirebaseAuthChange(callback) {
  return onAuthStateChanged(auth(), callback);
}
