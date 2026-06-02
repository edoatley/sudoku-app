# Arrow: Navigation

**Status**: PLANNED
**Created**: 2026-05-31

## Intent

Replace the boolean-per-dialog navigation pattern in `Header.jsx` and `App.jsx` with a `currentView` enum state machine. Player-facing flows (edit profile, puzzle history, statistics, league table) become full-screen views instead of MUI Dialogs.

## Spec → Code Trace

| Spec | Code Location | Status |
|------|--------------|--------|
| NAV-STATE-001 | `ui/src/App.jsx` — `currentView` state | [ ] |
| NAV-STATE-002 | `ui/src/App.jsx` — `useState('game')` | [ ] |
| NAV-STATE-003 | `ui/src/App.jsx` — `viewStack` state | [ ] |
| NAV-STATE-004 | `ui/src/App.jsx` — `navigateTo()` | [ ] |
| NAV-STATE-005 | `ui/src/App.jsx` — `navigateBack()` | [ ] |
| NAV-KBD-001 | `ui/src/App.jsx` — `isModalOpen` | [ ] |
| NAV-HDR-001 | `ui/src/components/Header.jsx` — `onNavigate` prop | [ ] |
| NAV-HDR-002 | `ui/src/components/Header.jsx` — profile menu item | [ ] |
| NAV-HDR-003 | `ui/src/components/Header.jsx` — history menu item | [ ] |
| NAV-HDR-004 | `ui/src/components/Header.jsx` — statistics menu item | [ ] |
| NAV-HDR-005 | `ui/src/components/Header.jsx` — leaderboard menu item | [ ] |
| NAV-HDR-006 | `ui/src/components/Header.jsx` — `EmojiEvents` icon | [ ] |
| NAV-HDR-007 | Delete `EditProfileDialog.jsx`, `PuzzleHistoryDialog.jsx`, `StatisticsDialog.jsx` | [ ] |
| NAV-VIEW-001 | `ui/src/components/views/AppView.jsx` | [ ] |
| NAV-VIEW-002 | `ui/src/components/views/AppView.jsx` — null when `'game'` | [ ] |
| NAV-VIEW-003 | `ui/src/components/views/AppView.jsx` — fixed overlay | [ ] |
| NAV-VIEW-004 | `ui/src/components/views/AppView.jsx` — `<Slide>` | [ ] |
| NAV-VIEW-005 | `ui/src/components/views/AppView.jsx` → `ProfileView` | [ ] |
| NAV-VIEW-006 | `ui/src/components/views/AppView.jsx` → `HistoryView` | [ ] |
| NAV-VIEW-007 | `ui/src/components/views/AppView.jsx` → `StatisticsView` | [ ] |
| NAV-VIEW-008 | `ui/src/components/views/AppView.jsx` → `LeaderboardView` | [ ] |
| NAV-LAYOUT-001 | All view files — sticky AppBar + ArrowBack | [ ] |
| NAV-LAYOUT-002 | All view files — `onBack()` handler | [ ] |
| NAV-LAYOUT-003 | All view files — scrollable content area | [ ] |
| NAV-LAYOUT-004 | All view files — no sub-navigation | [ ] |

## Files

**Modified:**
- `ui/src/App.jsx`
- `ui/src/components/Header.jsx`

**Created:**
- `ui/src/components/views/AppView.jsx`
- `ui/src/components/views/ProfileView.jsx`
- `ui/src/components/views/HistoryView.jsx`
- `ui/src/components/views/StatisticsView.jsx`
- `ui/src/components/views/LeaderboardView.jsx`

**Deleted:**
- `ui/src/components/EditProfileDialog.jsx`
- `ui/src/components/PuzzleHistoryDialog.jsx`
- `ui/src/components/StatisticsDialog.jsx`

## References

- LLD: `docs/llds/navigation.md`
- Specs: `docs/specs/navigation-specs.md`
- Related: `docs/arrows/league-table.md` (adds `LeaderboardView`)
