// @spec SC-UI-001, SC-UI-002, SC-UI-003, SC-UI-004, SC-RL-008
import Fab from '@mui/material/Fab';
import SchoolIcon from '@mui/icons-material/School';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import CoachPanel from './CoachPanel.jsx';
import { useCoachSession } from '../../hooks/useCoachSession.js';

const MONTHLY_TOKEN_LIMIT = 100_000;

export default function CoachWidget({ currentGrid, setHighlightCells, playerProfile }) {
  const theme = useTheme();
  // @spec SC-UI-001 — desktop only; hide on screens narrower than md breakpoint
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const { isOpen, open, close, history, isLoading, sendMessage } = useCoachSession({
    currentGrid,
    setHighlightCells,
  });

  // @spec SC-RL-008 — hide FAB when AI coach is disabled in profile
  if (isMobile || playerProfile?.aiCoachEnabled === false) return null;

  const tokensUsed = playerProfile?.coachTokensUsedThisMonth ?? 0;

  return (
    <>
      {isOpen && (
        <CoachPanel
          history={history}
          isLoading={isLoading}
          onSend={sendMessage}
          onClose={close}
          tokensUsed={tokensUsed}
          monthlyTokenLimit={MONTHLY_TOKEN_LIMIT}
        />
      )}
      <Fab
        color="primary"
        aria-label={isOpen ? 'Close coach' : 'Open coach'}
        onClick={isOpen ? close : open}
        sx={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1300 }}
      >
        <SchoolIcon />
      </Fab>
    </>
  );
}
