import { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Avatar from '@mui/material/Avatar';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Snackbar from '@mui/material/Snackbar';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import GridOnIcon from '@mui/icons-material/GridOn';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import LogoutIcon from '@mui/icons-material/Logout';
import PersonIcon from '@mui/icons-material/Person';
import BarChartIcon from '@mui/icons-material/BarChart';
import HistoryIcon from '@mui/icons-material/History';

function formatTime(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function getUserInitial(user) {
  const loginId = user?.signInDetails?.loginId ?? user?.username ?? '';
  return loginId.charAt(0).toUpperCase() || '?';
}

export default function Header({
  elapsedSeconds,
  timerRunning,
  gameStarted,
  user,
  onSignOut,
  onPause,
  onResume,
  isPaused,
  minimal,
}) {
  const [anchorEl, setAnchorEl] = useState(null);
  const [comingSoonOpen, setComingSoonOpen] = useState(false);

  const handleAvatarClick = (e) => setAnchorEl(e.currentTarget);
  const handleMenuClose = () => setAnchorEl(null);

  const handleComingSoon = () => {
    handleMenuClose();
    setComingSoonOpen(true);
  };

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

        {!minimal && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
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

            {gameStarted && (
              <IconButton
                onClick={isPaused ? onResume : onPause}
                sx={{ color: 'primary.contrastText' }}
                size="small"
                aria-label={isPaused ? 'Resume game' : 'Pause game'}
              >
                {isPaused ? <PlayArrowIcon /> : <PauseIcon />}
              </IconButton>
            )}

            {user && (
              <>
                <IconButton onClick={handleAvatarClick} size="small" aria-label="User menu">
                  <Avatar
                    sx={{
                      bgcolor: 'primary.dark',
                      width: 32,
                      height: 32,
                      fontSize: '0.9rem',
                      fontWeight: 'bold',
                    }}
                  >
                    {getUserInitial(user)}
                  </Avatar>
                </IconButton>
                <Menu
                  anchorEl={anchorEl}
                  open={Boolean(anchorEl)}
                  onClose={handleMenuClose}
                  transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                  anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                >
                  <MenuItem onClick={handleComingSoon}>
                    <ListItemIcon><PersonIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Change Avatar</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={handleComingSoon}>
                    <ListItemIcon><HistoryIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Puzzle History</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={handleComingSoon}>
                    <ListItemIcon><BarChartIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Statistics</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={() => { handleMenuClose(); onSignOut(); }}>
                    <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Sign Out</ListItemText>
                  </MenuItem>
                </Menu>
              </>
            )}
          </Box>
        )}
      </Toolbar>

      <Snackbar
        open={comingSoonOpen}
        autoHideDuration={2500}
        onClose={() => setComingSoonOpen(false)}
        message="Coming soon"
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </AppBar>
  );
}
