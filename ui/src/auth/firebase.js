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
  const user = auth().currentUser;
  return user ? user.getIdToken() : undefined;
}

export function firebaseGetEmail() {
  return auth().currentUser?.email ?? null;
}

// Subscribe to auth state; returns the unsubscribe function.
export function onFirebaseAuthChange(callback) {
  return onAuthStateChanged(auth(), callback);
}
