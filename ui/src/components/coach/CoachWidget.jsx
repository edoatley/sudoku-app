// @spec SC-UI-001, SC-UI-002, SC-UI-003, SC-UI-004
import Fab from '@mui/material/Fab';
import SchoolIcon from '@mui/icons-material/School';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import CoachPanel from './CoachPanel.jsx';
import { useCoachSession } from '../../hooks/useCoachSession.js';

export default function CoachWidget({ currentGrid, setHighlightCells }) {
  const theme = useTheme();
  // @spec SC-UI-001 — desktop only; hide on screens narrower than md breakpoint
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const { isOpen, open, close, history, isLoading, sendMessage } = useCoachSession({
    currentGrid,
    setHighlightCells,
  });

  if (isMobile) return null;

  return (
    <>
      {isOpen && (
        <CoachPanel
          history={history}
          isLoading={isLoading}
          onSend={sendMessage}
          onClose={close}
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
