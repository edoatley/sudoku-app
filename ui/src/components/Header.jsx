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
import Divider from '@mui/material/Divider';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import GridOnIcon from '@mui/icons-material/GridOn';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import LogoutIcon from '@mui/icons-material/Logout';
import PersonIcon from '@mui/icons-material/Person';
import BarChartIcon from '@mui/icons-material/BarChart';
import HistoryIcon from '@mui/icons-material/History';
import MenuIcon from '@mui/icons-material/Menu';
import AddIcon from '@mui/icons-material/Add';
import ImageIcon from '@mui/icons-material/Image';
import BugReportIcon from '@mui/icons-material/BugReport';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import AvatarPickerDialog from './AvatarPickerDialog.jsx';
import { AVATAR_ICONS } from '../utils/avatarIcons.js';
import PuzzleHistoryDialog from './PuzzleHistoryDialog.jsx';
import StatisticsDialog from './StatisticsDialog.jsx';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';

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

const DEMO_TECHNIQUES = [
  { slug: 'full-house',    label: 'Full House demo' },
  { slug: 'naked-single',  label: 'Naked Single demo' },
  { slug: 'naked-pair',    label: 'Naked Pair demo' },
  { slug: 'hidden-single', label: 'Hidden Single demo' },
  { slug: 'pointing-pair', label: 'Pointing Pair demo' },
  { slug: 'naked-triple',  label: 'Naked Triple demo' },
  { slug: 'hidden-pair',   label: 'Hidden Pair demo' },
  { slug: 'hidden-triple', label: 'Hidden Triple demo' },
  { slug: 'x-wing',        label: 'X-Wing demo' },
  { slug: 'swordfish',     label: 'Swordfish demo' },
  { slug: 'y-wing',        label: 'Y-Wing demo' },
];

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
  onNewGame,
  onImport,
  onDemoGame,
  colorMode,
  onToggleColorMode,
  avatar,
  onAvatarChange,
  history,
}) {
  const [anchorEl, setAnchorEl] = useState(null);
  const [gameMenuAnchorEl, setGameMenuAnchorEl] = useState(null);
  const [devMenuAnchorEl, setDevMenuAnchorEl] = useState(null);
  const [avatarDialogOpen, setAvatarDialogOpen] = useState(false);
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false);
  const [statsDialogOpen, setStatsDialogOpen] = useState(false);

  const handleAvatarClick = (e) => setAnchorEl(e.currentTarget);
  const handleMenuClose = () => setAnchorEl(null);

  const handleGameMenuOpen = (e) => setGameMenuAnchorEl(e.currentTarget);
  const handleGameMenuClose = () => { setGameMenuAnchorEl(null); setDevMenuAnchorEl(null); };

  const handleDevMenuOpen = (e) => setDevMenuAnchorEl(e.currentTarget);
  const handleDevMenuClose = () => setDevMenuAnchorEl(null);

  const avatarIconEntry = AVATAR_ICONS.find((a) => a.name === avatar);
  const AvatarIcon = avatarIconEntry?.Component ?? null;

  const displayName = !MOCK_API && !SKIP_AUTH
    ? (user?.signInDetails?.loginId ?? user?.username ?? null)
    : (user?.username ?? 'Guest');

  return (
    <AppBar position="static" elevation={0} sx={{ bgcolor: 'primary.main' }}>
      <Toolbar sx={{ justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {!minimal && (
            <>
              <IconButton
                onClick={handleGameMenuOpen}
                sx={{ color: 'primary.contrastText', borderRadius: 1 }}
                size="small"
                aria-label="Game menu"
              >
                <MenuIcon />
                <Typography
                  variant="caption"
                  sx={{
                    color: 'primary.contrastText',
                    ml: 0.5,
                    fontWeight: 600,
                    display: { xs: 'none', sm: 'inline' },
                    textTransform: 'uppercase',
                    letterSpacing: 0.5,
                  }}
                >
                  Game
                </Typography>
              </IconButton>
              <Menu
                anchorEl={gameMenuAnchorEl}
                open={Boolean(gameMenuAnchorEl)}
                onClose={handleGameMenuClose}
                transformOrigin={{ horizontal: 'left', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'left', vertical: 'bottom' }}
              >
                <Box sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider', mb: 0.5 }}>
                  <Typography variant="overline" color="text.secondary" lineHeight={1.5}>
                    Game
                  </Typography>
                </Box>
                <MenuItem onClick={() => { handleGameMenuClose(); onNewGame?.(); }}>
                  <ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
                  <ListItemText>New Game</ListItemText>
                </MenuItem>
                {onImport && (
                  <MenuItem onClick={() => { handleGameMenuClose(); onImport(); }}>
                    <ListItemIcon><ImageIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Import from Image</ListItemText>
                  </MenuItem>
                )}
                {onDemoGame && <Divider />}
                {onDemoGame && (
                  <MenuItem onClick={handleDevMenuOpen}>
                    <ListItemIcon><BugReportIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Developer</ListItemText>
                    <Typography variant="body2" sx={{ ml: 1, color: 'text.secondary' }}>▶</Typography>
                  </MenuItem>
                )}
              </Menu>
              {onDemoGame && (
                <Menu
                  anchorEl={devMenuAnchorEl}
                  open={Boolean(devMenuAnchorEl)}
                  onClose={handleDevMenuClose}
                  transformOrigin={{ horizontal: 'left', vertical: 'top' }}
                  anchorOrigin={{ horizontal: 'right', vertical: 'top' }}
                >
                  {DEMO_TECHNIQUES.map(({ slug, label }) => (
                    <MenuItem
                      key={slug}
                      onClick={() => { handleGameMenuClose(); onDemoGame(slug); }}
                    >
                      <ListItemText>{label}</ListItemText>
                    </MenuItem>
                  ))}
                </Menu>
              )}
            </>
          )}
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

            {(user || MOCK_API || SKIP_AUTH) && (
              <>
                <IconButton
                  onClick={handleAvatarClick}
                  size="small"
                  aria-label="Account menu"
                  sx={{ color: 'primary.contrastText', borderRadius: 1, gap: 0.5 }}
                >
                  <Avatar
                    sx={{
                      bgcolor: 'primary.dark',
                      width: 32,
                      height: 32,
                      fontSize: '0.9rem',
                      fontWeight: 'bold',
                    }}
                  >
                    {AvatarIcon ? <AvatarIcon sx={{ fontSize: 20 }} /> : getUserInitial(user)}
                  </Avatar>
                  {displayName && (
                    <Typography
                      variant="caption"
                      sx={{
                        color: 'primary.contrastText',
                        fontWeight: 600,
                        display: { xs: 'none', sm: 'inline' },
                        maxWidth: 120,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {displayName}
                    </Typography>
                  )}
                </IconButton>
                <Menu
                  anchorEl={anchorEl}
                  open={Boolean(anchorEl)}
                  onClose={handleMenuClose}
                  transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                  anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                >
                  {displayName && (
                    <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider', mb: 0.5 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36 }}>
                          {AvatarIcon ? <AvatarIcon sx={{ fontSize: 22 }} /> : getUserInitial(user)}
                        </Avatar>
                        <Box>
                          <Typography variant="body2" fontWeight={600} noWrap sx={{ maxWidth: 180 }}>
                            {displayName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">My Account</Typography>
                        </Box>
                      </Box>
                    </Box>
                  )}
                  <MenuItem onClick={() => { handleMenuClose(); setAvatarDialogOpen(true); }}>
                    <ListItemIcon><PersonIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Change Avatar</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={() => { handleMenuClose(); setHistoryDialogOpen(true); }}>
                    <ListItemIcon><HistoryIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Puzzle History</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={() => { handleMenuClose(); setStatsDialogOpen(true); }}>
                    <ListItemIcon><BarChartIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Statistics</ListItemText>
                  </MenuItem>
                  <Divider />
                  <MenuItem onClick={onToggleColorMode}>
                    <ListItemIcon>
                      {colorMode === 'dark' ? <Brightness7Icon fontSize="small" /> : <Brightness4Icon fontSize="small" />}
                    </ListItemIcon>
                    <ListItemText>
                      Dark Mode
                      <Typography component="span" variant="caption" sx={{ ml: 1, color: 'text.secondary' }}>
                        {colorMode === 'dark' ? 'On' : 'Off'}
                      </Typography>
                    </ListItemText>
                  </MenuItem>
                  {onSignOut && (
                    <>
                      <Divider />
                      <MenuItem onClick={() => { handleMenuClose(); onSignOut(); }}>
                        <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
                        <ListItemText>Sign Out</ListItemText>
                      </MenuItem>
                    </>
                  )}
                </Menu>
              </>
            )}
          </Box>
        )}
      </Toolbar>

      <AvatarPickerDialog
        open={avatarDialogOpen}
        currentAvatar={avatar}
        onSelect={(name) => { onAvatarChange?.(name); setAvatarDialogOpen(false); }}
        onClose={() => setAvatarDialogOpen(false)}
      />
      <PuzzleHistoryDialog
        open={historyDialogOpen}
        history={history ?? []}
        onClose={() => setHistoryDialogOpen(false)}
      />
      <StatisticsDialog
        open={statsDialogOpen}
        history={history ?? []}
        onClose={() => setStatsDialogOpen(false)}
      />
    </AppBar>
  );
}
