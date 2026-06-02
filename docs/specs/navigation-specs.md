# Navigation Specs

## View State

- [ ] **NAV-STATE-001**: The system shall maintain a `currentView` state in `App.jsx` that takes one of the values: `'game'`, `'profile'`, `'history'`, `'statistics'`, `'leaderboard'`.
- [ ] **NAV-STATE-002**: The initial value of `currentView` shall be `'game'` on every app load.
- [ ] **NAV-STATE-003**: The system shall maintain a `viewStack` array in `App.jsx` recording the history of views navigated from.
- [ ] **NAV-STATE-004**: When `navigateTo(view)` is called, the system shall push the current view onto `viewStack` and set `currentView` to the new view.
- [ ] **NAV-STATE-005**: When `navigateBack()` is called, the system shall pop the most recent entry from `viewStack` and set `currentView` to that value; if the stack is empty it shall set `currentView` to `'game'`.

## Keyboard Suppression

- [ ] **NAV-KBD-001**: The system shall pass `isModalOpen = currentView !== 'game'` to `useSudokuGame` so that keyboard grid input is suppressed whenever any full-screen view is active.

## Header Navigation Contract

- [ ] **NAV-HDR-001**: `Header.jsx` shall accept an `onNavigate(view)` prop and call it when the user selects a player menu item.
- [ ] **NAV-HDR-002**: The "Edit Profile" menu item shall call `onNavigate('profile')`.
- [ ] **NAV-HDR-003**: The "Puzzle History" menu item shall call `onNavigate('history')`.
- [ ] **NAV-HDR-004**: The "Statistics" menu item shall call `onNavigate('statistics')`.
- [ ] **NAV-HDR-005**: The "League Table" menu item shall call `onNavigate('leaderboard')`.
- [ ] **NAV-HDR-006**: The "League Table" menu item shall use the `EmojiEvents` MUI icon.
- [ ] **NAV-HDR-007**: `Header.jsx` shall not render any MUI `<Dialog>` components for the player menu flows; all three dialog files (`EditProfileDialog.jsx`, `PuzzleHistoryDialog.jsx`, `StatisticsDialog.jsx`) shall be deleted.

## AppView Routing

- [ ] **NAV-VIEW-001**: The system shall render an `AppView` component that receives `currentView` and `navigateBack` as props.
- [ ] **NAV-VIEW-002**: When `currentView === 'game'`, `AppView` shall render nothing (the game is always visible underneath).
- [ ] **NAV-VIEW-003**: When `currentView` is not `'game'`, `AppView` shall render a `position: fixed; inset: 0; zIndex: 1200` overlay covering the full viewport.
- [ ] **NAV-VIEW-004**: The overlay shall use an MUI `<Slide direction="left">` transition when entering and exiting.
- [ ] **NAV-VIEW-005**: `AppView` shall render `ProfileView` when `currentView === 'profile'`.
- [ ] **NAV-VIEW-006**: `AppView` shall render `HistoryView` when `currentView === 'history'`.
- [ ] **NAV-VIEW-007**: `AppView` shall render `StatisticsView` when `currentView === 'statistics'`.
- [ ] **NAV-VIEW-008**: `AppView` shall render `LeaderboardView` when `currentView === 'leaderboard'`.

## Full-Screen View Layout Standard

- [ ] **NAV-LAYOUT-001**: Every full-screen view shall begin with a sticky MUI `<AppBar>` containing a `<Toolbar>` with a back `<IconButton>` using the `ArrowBack` icon on the leading edge.
- [ ] **NAV-LAYOUT-002**: Pressing the back button in any view shall call `onBack()` which resolves to `navigateBack()`.
- [ ] **NAV-LAYOUT-003**: Every full-screen view shall have scrollable content below the sticky AppBar within a `Box` with `flex: 1` and responsive padding (`p: { xs: 2, sm: 3 }`).
- [ ] **NAV-LAYOUT-004**: No full-screen view shall have internal sub-navigation deeper than one level from `'game'`.
