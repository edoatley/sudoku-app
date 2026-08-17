# Navigation Specs

## View State

- [x] **NAV-STATE-001**: The system shall maintain a `currentView` state in `App.jsx` that takes one of the values: `'game'`, `'profile'`, `'history'`, `'statistics'`, `'leaderboard'`.
- [x] **NAV-STATE-002**: The initial value of `currentView` shall be `'game'` on every app load.
- [x] **NAV-STATE-003**: The system shall maintain a `viewStack` array in `App.jsx` recording the history of views navigated from.
- [x] **NAV-STATE-004**: When `navigateTo(view)` is called, the system shall push the current view onto `viewStack` and set `currentView` to the new view.
- [x] **NAV-STATE-005**: When `navigateBack()` is called, the system shall pop the most recent entry from `viewStack` and set `currentView` to that value; if the stack is empty it shall set `currentView` to `'game'`.

## Keyboard Suppression

- [x] **NAV-KBD-001**: The system shall pass `isModalOpen = currentView !== 'game'` to `useSudokuGame` so that keyboard grid input is suppressed whenever any full-screen view is active.

## Header Navigation Contract

- [x] **NAV-HDR-001**: `Header.jsx` shall accept an `onNavigate(view)` prop and call it when the user selects a player menu item.
- [x] **NAV-HDR-002**: The "Edit Profile" menu item shall call `onNavigate('profile')`.
- [x] **NAV-HDR-003**: The "Puzzle History" menu item shall call `onNavigate('history')`.
- [x] **NAV-HDR-004**: The "Statistics" menu item shall call `onNavigate('statistics')`.
- [x] **NAV-HDR-005**: The "League Table" menu item shall call `onNavigate('leaderboard')`.
- [x] **NAV-HDR-006**: The "League Table" menu item shall use the `EmojiEvents` MUI icon.
- [x] **NAV-HDR-007**: `Header.jsx` shall not render any MUI `<Dialog>` components for the player menu flows; all three dialog files (`EditProfileDialog.jsx`, `PuzzleHistoryDialog.jsx`, `StatisticsDialog.jsx`) shall be deleted.

## AppView Routing

- [x] **NAV-VIEW-001**: The system shall render an `AppView` component that receives `currentView` and `navigateBack` as props.
- [x] **NAV-VIEW-002**: When `currentView === 'game'`, `AppView` shall render nothing (the game is always visible underneath).
- [x] **NAV-VIEW-003**: When `currentView` is not `'game'`, `AppView` shall render a `position: fixed; inset: 0; zIndex: 1200` overlay covering the full viewport.
- [x] **NAV-VIEW-004**: The overlay shall use an MUI `<Slide direction="left">` transition when entering and exiting.
- [x] **NAV-VIEW-005**: `AppView` shall render `ProfileView` when `currentView === 'profile'`.
- [x] **NAV-VIEW-006**: `AppView` shall render `HistoryView` when `currentView === 'history'`.
- [x] **NAV-VIEW-007**: `AppView` shall render `StatisticsView` when `currentView === 'statistics'`.
- [x] **NAV-VIEW-008**: `AppView` shall render `LeaderboardView` when `currentView === 'leaderboard'`.

## Full-Screen View Layout Standard

- [x] **NAV-LAYOUT-001**: Every full-screen view shall begin with a sticky MUI `<AppBar>` containing a `<Toolbar>` with a back `<IconButton>` using the `ArrowBack` icon on the leading edge.
- [x] **NAV-LAYOUT-002**: Pressing the back button in any view shall call `onBack()` which resolves to `navigateBack()`.
- [x] **NAV-LAYOUT-003**: Every full-screen view shall have scrollable content below the sticky AppBar within a `Box` with `flex: 1` and responsive padding (`p: { xs: 2, sm: 3 }`).
- [x] **NAV-LAYOUT-004**: No full-screen view shall have internal sub-navigation deeper than one level from `'game'`.
