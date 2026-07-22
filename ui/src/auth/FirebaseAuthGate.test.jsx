import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import FirebaseAuthGate from './FirebaseAuthGate.jsx';

let authCallback;
const signInMock = vi.fn();

vi.mock('./firebase.js', () => ({
  onFirebaseAuthChange: (cb) => {
    authCallback = cb;
    return () => {};
  },
  firebaseSignInWithGoogle: (...args) => signInMock(...args),
  firebaseSignOut: vi.fn(),
}));

describe('FirebaseAuthGate', () => {
  afterEach(() => {
    authCallback = undefined;
    signInMock.mockClear();
  });

  it('shows the Google sign-in screen when signed out', async () => {
    render(<FirebaseAuthGate>{() => <div>APP</div>}</FirebaseAuthGate>);
    await waitFor(() => expect(authCallback).toBeDefined());
    act(() => authCallback(null));

    expect(await screen.findByRole('button', { name: /sign in with google/i })).toBeTruthy();
    expect(screen.queryByText('APP')).toBeNull();
  });

  it('clicking sign-in triggers the Google popup flow', async () => {
    render(<FirebaseAuthGate>{() => <div>APP</div>}</FirebaseAuthGate>);
    await waitFor(() => expect(authCallback).toBeDefined());
    act(() => authCallback(null));

    fireEvent.click(await screen.findByRole('button', { name: /sign in with google/i }));
    await waitFor(() => expect(signInMock).toHaveBeenCalled());
  });

  it('renders children with a normalized user (email as loginId) when signed in', async () => {
    render(<FirebaseAuthGate>{({ user }) => <div>Hello {user.signInDetails.loginId}</div>}</FirebaseAuthGate>);
    await waitFor(() => expect(authCallback).toBeDefined());
    act(() => authCallback({ email: 'bob@gmail.com', uid: 'x' }));

    expect(await screen.findByText('Hello bob@gmail.com')).toBeTruthy();
  });
});
