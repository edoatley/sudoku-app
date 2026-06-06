# Navigation

**Created**: 2026-05-31
**Status**: Planned

## Context and Current State

Navigation is currently implicit: every player-facing flow (edit profile, puzzle history, statistics) is opened as an MUI Dialog via a boolean state variable in `Header.jsx`. As of this writing there are three separate `open` booleans (`editProfileOpen`, `historyDialogOpen`, `statsDialogOpen`) threaded down from `Header` and rendered as three `<Dialog>` components at the bottom of `Header.jsx`.

This pattern does not scale to adding a League Table screen and does not satisfy the requirement for "fully fledged screens not modals". This LLD replaces the pattern with an explicit `currentView` state machine in `App.jsx`.

React Router was considered and rejected: the `<Authenticator>` component from `@aws-amplify/ui-react` wraps the entire app in `App.jsx`; restructuring it to sit inside a `<BrowserRouter>` carries non-trivial auth flow risk for no user-visible benefit given the known-user, single-device usage pattern.

Files: `ui/src/App.jsx`, `ui/src/components/Header.jsx`, `ui/src/components/views/AppView.jsx`, all files under `ui/src/components/views/`.

## View Enum

Five views are defined:

| Value | What is shown |
|-------|---------------|
| `'game'` | The Sudoku game (default) |
| `'profile'` | Edit profile full-screen view |
| `'history'` | Puzzle history full-screen view |
| `'statistics'` | Statistics full-screen view |
| `'leaderboard'` | League table full-screen view |

`'game'` is always the initial view on app load. The game grid is always rendered underneath — views slide over it rather than replacing it.

## State Machine (`App.jsx`)

```js
const [currentView, setCurrentView] = useState('game');
const [viewStack, setViewStack] = useState([]);

function navigateTo(view) {
  setViewStack(prev => [...prev, currentView]);
  setCurrentView(view);
}

function navigateBack() {
  setViewStack(prev => {
    const next = [...prev];
    const previous = next.pop() ?? 'game';
    setCurrentView(previous);
    return next;
  });
}
```

`navigateTo` and `navigateBack` are passed as props to `<Header>` (for menu-driven navigation) and to `<AppView>` (for back-arrow navigation within views).

## Keyboard Suppression

The existing `isModalOpen` guard in `App.jsx` (which suppresses keyboard input to the grid while a dialog is open) becomes:

```js
const isModalOpen = currentView !== 'game';
```

This is passed unchanged to `useSudokuGame` — no change to keyboard handling logic.

## Header as Navigation Coordinator

`Header.jsx` stops managing `open` state for the three dialogs. Instead it receives `onNavigate(view)` as a prop and calls it from the avatar menu `<MenuItem>` handlers:

- "Edit Profile" → `onNavigate('profile')`
- "Puzzle History" → `onNavigate('history')`
- "Statistics" → `onNavigate('statistics')`
- "League Table" → `onNavigate('leaderboard')`

The three `<Dialog>` render calls and their corresponding imports are removed from `Header.jsx`. The dialog component files (`EditProfileDialog.jsx`, `PuzzleHistoryDialog.jsx`, `StatisticsDialog.jsx`) are deleted.

## AppView Component

`AppView.jsx` is a thin routing shell:

```jsx
function AppView({ currentView, navigateBack, ...viewProps }) {
  if (currentView === 'game') return null;
  return (
    <Slide direction="left" in mountOnEnter unmountOnExit>
      <Box sx={{ position: 'fixed', inset: 0, zIndex: 1200, overflow: 'auto',
                  bgcolor: 'background.default' }}>
        {currentView === 'profile'     && <ProfileView     onBack={navigateBack} {...viewProps} />}
        {currentView === 'history'     && <HistoryView     onBack={navigateBack} {...viewProps} />}
        {currentView === 'statistics'  && <StatisticsView  onBack={navigateBack} {...viewProps} />}
        {currentView === 'leaderboard' && <LeaderboardView onBack={navigateBack} {...viewProps} />}
      </Box>
    </Slide>
  );
}
```

`position: fixed; inset: 0` ensures the view covers the full viewport at `zIndex: 1200` (above the MUI AppBar at 1100). `overflow: auto` allows scrolling within the view.

## Full-Screen View Layout Standard

Every view must follow this layout structure:

```jsx
<Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
  {/* Sticky top bar */}
  <AppBar position="sticky" color="default" elevation={1}>
    <Toolbar>
      <IconButton edge="start" onClick={onBack}><ArrowBack /></IconButton>
      <Typography variant="h6" sx={{ flex: 1 }}>{title}</Typography>
      {/* optional trailing action */}
    </Toolbar>
  </AppBar>

  {/* Scrollable content */}
  <Box sx={{ flex: 1, p: { xs: 2, sm: 3 } }}>
    {/* view content */}
  </Box>
</Box>
```

The back button always calls `onBack()`. No view has its own internal navigation — depth is always 1 from the game view.

## Decisions

| Decision | Chosen | Rationale |
|----------|--------|-----------|
| Navigation mechanism | `currentView` enum + stack | No new dependency; safe with Amplify `<Authenticator>` wrapping; consistent with hooks-only state philosophy |
| React Router | Rejected | Restructuring `<Authenticator>` carries auth-flow risk not warranted for 4 users |
| Overlay strategy | `position: fixed` Box at `zIndex: 1200` | Covers AppBar and game without unmounting the game component |
| Transition | MUI `<Slide direction="left">` | Standard mobile-app feel; hardware-accelerated; no extra dependency |
| Back navigation | Stack of previous views | Supports multi-level navigation if added in future without coupling to URL history |

## References

- Depends on: React Frontend (`App.jsx`, `Header.jsx`)
- Depended on by: Profile, History, Statistics, League Table views
- Specs: `docs/specs/navigation-specs.md`
- Arrow: `docs/arrows/navigation.md`
