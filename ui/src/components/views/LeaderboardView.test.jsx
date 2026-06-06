// @spec LT-UI-006, LT-UI-007, LT-UI-008, LT-UI-009, LT-UI-010, LT-UI-011, LT-UI-012
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import LeaderboardView from './LeaderboardView.jsx';

vi.mock('../../hooks/useLeaderboard.js', () => ({
  useLeaderboard: vi.fn(),
}));

import { useLeaderboard } from '../../hooks/useLeaderboard.js';

const MOCK_ENTRIES = [
  {
    userId: 'user-1',
    displayName: 'Ed',
    avatarKey: 'SportsBasketball',
    rank: 1,
    totalWins: 10,
    totalGames: 12,
    avgScore: 185,
    avgElapsedSeconds: 241,
    bestTimeByDifficulty: { easy: 95, medium: 241 },
  },
  {
    userId: 'user-2',
    displayName: 'Alice',
    avatarKey: 'DirectionsRun',
    rank: 2,
    totalWins: 7,
    totalGames: 9,
    avgScore: 140,
    avgElapsedSeconds: 310,
    bestTimeByDifficulty: { easy: 110 },
  },
];

const onBack = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
});

// @spec LT-UI-006
describe('LeaderboardView — title and back navigation', () => {
  it('renders "League Table" as the title', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByText(/league table/i)).toBeTruthy();
  });

  it('calls onBack when the back button is pressed', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    fireEvent.click(screen.getByRole('button', { name: /back/i }));
    expect(onBack).toHaveBeenCalledOnce();
  });
});

// @spec LT-UI-007
describe('LeaderboardView — player cards', () => {
  it('renders a card for each player entry', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByText('Ed')).toBeTruthy();
    expect(screen.getByText('Alice')).toBeTruthy();
  });

  it('displays total wins for each player', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByText(/10/)).toBeTruthy(); // Ed's 10 wins
    expect(screen.getByText(/7/)).toBeTruthy();  // Alice's 7 wins
  });

  it('displays average score for each player', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByText(/185/)).toBeTruthy();
    expect(screen.getByText(/140/)).toBeTruthy();
  });
});

// @spec LT-UI-008
describe('LeaderboardView — rank badges', () => {
  it('renders rank 1 for the first entry', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByTestId('rank-badge-1')).toBeTruthy();
  });

  it('renders rank 2 for the second entry', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByTestId('rank-badge-2')).toBeTruthy();
  });

  it('rank-1 badge has gold styling', () => {
    useLeaderboard.mockReturnValue({ leaderboard: MOCK_ENTRIES, loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    const badge = screen.getByTestId('rank-badge-1');
    expect(badge.dataset.gold).toBe('true');
  });
});

// @spec LT-UI-009
describe('LeaderboardView — best time chips', () => {
  it('shows best-time chip for each difficulty with a recorded time', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [MOCK_ENTRIES[0]], loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    // Ed has easy=95s and medium=241s — both should appear
    expect(screen.getByTestId('best-time-easy-user-1')).toBeTruthy();
    expect(screen.getByTestId('best-time-medium-user-1')).toBeTruthy();
  });

  it('does not show chip for difficulties with no recorded time', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [MOCK_ENTRIES[0]], loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    // Ed has no hard or imported time
    expect(screen.queryByTestId('best-time-hard-user-1')).toBeNull();
    expect(screen.queryByTestId('best-time-imported-user-1')).toBeNull();
  });
});

// @spec LT-UI-010
describe('LeaderboardView — loading state', () => {
  it('shows skeleton placeholders while loading', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: true, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getAllByTestId('leaderboard-skeleton').length).toBeGreaterThan(0);
  });

  it('does not show player cards while loading', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: true, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.queryByText('Ed')).toBeNull();
  });
});

// @spec LT-UI-011
describe('LeaderboardView — error state', () => {
  it('shows error alert when error is set', () => {
    const refresh = vi.fn();
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: false, error: 'Service unavailable', refresh });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByText(/service unavailable/i)).toBeTruthy();
  });

  it('calls refresh when retry button is clicked', () => {
    const refresh = vi.fn();
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: false, error: 'Timeout', refresh });
    render(<LeaderboardView onBack={onBack} />);
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(refresh).toHaveBeenCalledOnce();
  });
});

// @spec LT-UI-012
describe('LeaderboardView — empty state', () => {
  it('shows empty-state message when leaderboard is empty and not loading', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: false, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.getByText(/no games completed yet/i)).toBeTruthy();
  });

  it('does not show empty state while loading', () => {
    useLeaderboard.mockReturnValue({ leaderboard: [], loading: true, error: null, refresh: vi.fn() });
    render(<LeaderboardView onBack={onBack} />);
    expect(screen.queryByText(/no games completed yet/i)).toBeNull();
  });
});
