import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import GridOnIcon from '@mui/icons-material/GridOn';
import PauseIcon from '@mui/icons-material/Pause';

function formatTime(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export default function Header({ elapsedSeconds, timerRunning, gameStarted }) {
  return (
    <AppBar position="static" elevation={0} sx={{ bgcolor: 'primary.main' }}>
      <Toolbar sx={{ justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <GridOnIcon sx={{ fontSize: 28, color: 'primary.contrastText' }} />
          <Typography
            variant="h5"
            component="h1"
            fontWeight="bold"
            letterSpacing={2}
            sx={{ color: 'primary.contrastText', textTransform: 'uppercase' }}
          >
            Sudoku
          </Typography>
        </Box>

        {gameStarted && (
          <Chip
            icon={timerRunning ? <TimerOutlinedIcon /> : <PauseIcon />}
            label={formatTime(elapsedSeconds)}
            variant="outlined"
            sx={{
              color: 'primary.contrastText',
              borderColor: 'rgba(255,255,255,0.5)',
              '& .MuiChip-icon': { color: 'primary.contrastText' },
              fontFamily: 'monospace',
              fontSize: '1rem',
              fontWeight: 'bold',
              px: 1,
            }}
          />
        )}
      </Toolbar>
    </AppBar>
  );
}
