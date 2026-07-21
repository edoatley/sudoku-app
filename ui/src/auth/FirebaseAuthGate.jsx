import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';

const CENTERED = {
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
  minHeight: '100vh',
};

async function signInWithGoogle() {
  const { firebaseSignInWithGoogle } = await import('./firebase.js');
  await firebaseSignInWithGoogle();
}

async function signOut() {
  const { firebaseSignOut } = await import('./firebase.js');
  await firebaseSignOut();
}

// Shape the Firebase user like the Cognito user the rest of the app (e.g. Header) expects.
function normalizeUser(fbUser) {
  const loginId = fbUser.email ?? fbUser.displayName ?? fbUser.uid;
  return { username: loginId, signInDetails: { loginId } };
}

/**
 * Firebase (Identity Platform) login gate. Mirrors the Amplify {@code <Authenticator>} render-prop
 * API — renders {@code children({ user, signOut })} once signed in, otherwise a Google sign-in
 * screen. Google is the sole sign-in provider; there is no in-app native sign-up.
 *
 * @spec CP-GCP-030
 */
export default function FirebaseAuthGate({ children }) {
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState(null);

  useEffect(() => {
    let active = true;
    let unsubscribe;
    (async () => {
      const { onFirebaseAuthChange } = await import('./firebase.js');
      unsubscribe = onFirebaseAuthChange((fbUser) => {
        if (!active) return;
        setUser(fbUser);
        setLoading(false);
      });
    })();
    return () => {
      active = false;
      if (unsubscribe) unsubscribe();
    };
  }, []);

  if (loading) {
    return (
      <Box sx={CENTERED}>
        <CircularProgress />
      </Box>
    );
  }

  if (!user) {
    return (
      <Box sx={CENTERED}>
        <Stack spacing={3} sx={{ alignItems: 'center' }}>
          <Typography variant="h4">Sudoku</Typography>
          <Typography color="text.secondary">Sign in to play</Typography>
          <Button variant="contained" size="large" onClick={signInWithGoogle}>
            Sign in with Google
          </Button>
        </Stack>
      </Box>
    );
  }

  return children({ user: normalizeUser(user), signOut });
}
