// @spec LT-UI-006, LT-UI-007, LT-UI-008, LT-UI-009, LT-UI-010, LT-UI-011, LT-UI-012
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import { useLeaderboard } from '../../hooks/useLeaderboard.js';

const DIFFICULTY_LABEL = { easy: 'E', medium: 'M', hard: 'H', imported: 'I' };
const DIFFICULTY_COLOR = { easy: '#4caf50', medium: '#ff9800', hard: '#f44336', imported: '#9c27b0' };

function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function RankBadge({ rank }) {
  const isGold = rank === 1;
  return (
    <Box
      data-testid={`rank-badge-${rank}`}
      data-gold={isGold ? 'true' : 'false'}
      sx={{
        width: 36,
        height: 36,
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: isGold ? '#FFD700' : rank === 2 ? '#C0C0C0' : rank === 3 ? '#CD7F32' : 'action.selected',
        flexShrink: 0,
      }}
    >
      {isGold
        ? <EmojiEventsIcon sx={{ fontSize: 20, color: '#7a5900' }} />
        : <Typography variant="body2" fontWeight={700}>{rank}</Typography>}
    </Box>
  );
}

function PlayerCard({ entry }) {
  const bestTimes = entry.bestTimeByDifficulty ?? {};

  return (
    <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
        <RankBadge rank={entry.rank} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="body1" fontWeight={600} noWrap>{entry.displayName}</Typography>
          <Typography variant="caption" color="text.secondary">
            {entry.totalWins} wins · Avg score: {entry.avgScore}
          </Typography>
        </Box>
      </Box>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
        {Object.entries(bestTimes).map(([diff, secs]) => (
          <Chip
            key={diff}
            data-testid={`best-time-${diff}-${entry.userId}`}
            size="small"
            label={`${DIFFICULTY_LABEL[diff] ?? diff}: ${formatTime(secs)}`}
            sx={{ borderLeft: `3px solid ${DIFFICULTY_COLOR[diff] ?? '#90a4ae'}` }}
            variant="outlined"
          />
        ))}
      </Box>
    </Paper>
  );
}

export default function LeaderboardView({ onBack }) {
  const { leaderboard, loading, error, refresh } = useLeaderboard();

  return (
    <>
      <AppBar position="sticky" elevation={1}>
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={onBack} aria-label="Back">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ ml: 1, flex: 1 }}>League Table</Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ maxWidth: 600, mx: 'auto', p: 2 }}>
        {loading && (
          <Stack spacing={1}>
            {[1, 2, 3, 4].map((i) => (
              <Skeleton
                key={i}
                data-testid="leaderboard-skeleton"
                variant="rounded"
                height={80}
              />
            ))}
          </Stack>
        )}

        {!loading && error && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={refresh}>
                Retry
              </Button>
            }
            sx={{ mb: 2 }}
          >
            {error}
          </Alert>
        )}

        {!loading && !error && leaderboard.length === 0 && (
          <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
            No games completed yet.
          </Typography>
        )}

        {!loading && leaderboard.length > 0 && (
          <Stack spacing={1}>
            {leaderboard.map((entry) => (
              <PlayerCard key={entry.userId} entry={entry} />
            ))}
          </Stack>
        )}
      </Box>
    </>
  );
}
