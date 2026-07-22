import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.jsx';
import { AUTH_PROVIDER } from './auth/session.js';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';

// Cognito needs explicit Amplify configuration; the Firebase SDK self-initializes lazily
// (see auth/firebase.js), so the GCP build skips this block entirely.
if (!MOCK_API && !SKIP_AUTH && AUTH_PROVIDER === 'cognito') {
  const { Amplify } = await import('aws-amplify');
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
        userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
        loginWith: {
          oauth: {
            domain: import.meta.env.VITE_COGNITO_DOMAIN,
            scopes: ['openid', 'email', 'profile'],
            redirectSignIn: [`${window.location.origin}/`],
            redirectSignOut: [`${window.location.origin}/`],
            responseType: 'code',
          },
        },
      },
    },
  });
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>
);
