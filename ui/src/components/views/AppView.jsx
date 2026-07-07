// @spec NAV-STATE-001, NAV-STATE-002, NAV-STATE-004, NAV-LAYOUT-001
import Slide from '@mui/material/Slide';
import Box from '@mui/material/Box';
import ProfileView from './ProfileView.jsx';
import HistoryView from './HistoryView.jsx';
import StatisticsView from './StatisticsView.jsx';
import LeaderboardView from './LeaderboardView.jsx';

const PLAYER_VIEWS = ['profile', 'history', 'statistics', 'leaderboard'];

export default function AppView({
  currentView,
  navigateBack,
  playerProfile,
  currentAvatar,
  onProfileUpdate,
  history,
  onRefreshHistory,
}) {
  const visible = PLAYER_VIEWS.includes(currentView);

  return (
    <Slide direction="up" in={visible} mountOnEnter unmountOnExit>
      <Box
        sx={{
          position: 'fixed',
          inset: 0,
          zIndex: 1200,
          bgcolor: 'background.default',
          overflowY: 'auto',
        }}
      >
        {currentView === 'profile' && (
          <ProfileView
            navigateBack={navigateBack}
            playerProfile={playerProfile}
            currentAvatar={currentAvatar}
            onProfileUpdate={onProfileUpdate}
          />
        )}
        {currentView === 'history' && (
          <HistoryView navigateBack={navigateBack} history={history} onRefreshHistory={onRefreshHistory} />
        )}
        {currentView === 'statistics' && <StatisticsView navigateBack={navigateBack} history={history} />}
        {currentView === 'leaderboard' && <LeaderboardView onBack={navigateBack} />}
      </Box>
    </Slide>
  );
}
