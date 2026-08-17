# Arrow: Navigation

**Status**: OK
**Created**: 2026-05-31

## Intent

Replace the boolean-per-dialog navigation pattern in `Header.jsx` and `App.jsx` with a `currentView` enum state machine. Player-facing flows (edit profile, puzzle history, statistics, league table) become full-screen views instead of MUI Dialogs.

## Spec → Code Trace

| Spec | Code Location | Status |
|------|--------------|--------|
| NAV-STATE-001 | `ui/src/App.jsx` — `currentView` state | [x] |
| NAV-STATE-002 | `ui/src/App.jsx` — `useState('game')` | [x] |
| NAV-STATE-003 | `ui/src/App.jsx` — `viewStack` state | [x] |
| NAV-STATE-004 | `ui/src/App.jsx` — `navigateTo()` | [x] |
| NAV-STATE-005 | `ui/src/App.jsx` — `navigateBack()` | [x] |
| NAV-KBD-001 | `ui/src/App.jsx` — `isModalOpen` | [x] |
| NAV-HDR-001 | `ui/src/components/Header.jsx` — `onNavigate` prop | [x] |
| NAV-HDR-002 | `ui/src/components/Header.jsx` — profile menu item | [x] |
| NAV-HDR-003 | `ui/src/components/Header.jsx` — history menu item | [x] |
| NAV-HDR-004 | `ui/src/components/Header.jsx` — statistics menu item | [x] |
| NAV-HDR-005 | `ui/src/components/Header.jsx` — leaderboard menu item | [x] |
| NAV-HDR-006 | `ui/src/components/Header.jsx` — `EmojiEvents` icon | [x] |
| NAV-HDR-007 | Delete `EditProfileDialog.jsx`, `PuzzleHistoryDialog.jsx`, `StatisticsDialog.jsx` | [x] |
| NAV-VIEW-001 | `ui/src/components/views/AppView.jsx` | [x] |
| NAV-VIEW-002 | `ui/src/components/views/AppView.jsx` — null when `'game'` | [x] |
| NAV-VIEW-003 | `ui/src/components/views/AppView.jsx` — fixed overlay | [x] |
| NAV-VIEW-004 | `ui/src/components/views/AppView.jsx` — `<Slide>` | [x] |
| NAV-VIEW-005 | `ui/src/components/views/AppView.jsx` → `ProfileView` | [x] |
| NAV-VIEW-006 | `ui/src/components/views/AppView.jsx` → `HistoryView` | [x] |
| NAV-VIEW-007 | `ui/src/components/views/AppView.jsx` → `StatisticsView` | [x] |
| NAV-VIEW-008 | `ui/src/components/views/AppView.jsx` → `LeaderboardView` | [x] |
| NAV-LAYOUT-001 | All view files — sticky AppBar + ArrowBack | [x] |
| NAV-LAYOUT-002 | All view files — `onBack()` handler | [x] |
| NAV-LAYOUT-003 | All view files — scrollable content area | [x] |
| NAV-LAYOUT-004 | All view files — no sub-navigation | [x] |

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
