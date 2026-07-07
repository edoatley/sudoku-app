// @spec NAV-LAYOUT-001, NAV-LAYOUT-002, FE-UI-042, FE-UI-042a, GH-UI-005
import { useState, useCallback } from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import LightbulbOutlinedIcon from '@mui/icons-material/LightbulbOutlined';
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import StarOutlinedIcon from '@mui/icons-material/StarOutlined';

const DIFFICULTY_COLOR = {
  easy: '#4caf50',
  medium: '#ff9800',
  hard: '#f44336',
  imported: '#9c27b0',
};

function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString(undefined, { dateStyle: 'medium' });
}

function capitalize(str) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function computeSummary(history) {
  const wins = history.filter((e) => e.outcome === 'won');
  const times = wins.map((e) => e.elapsedSeconds).filter((n) => typeof n === 'number');
  const scores = wins.map((e) => e.score ?? 0).filter((s) => s > 0);
  let currentStreak = 0;
  for (const e of history) {
    if (e.outcome === 'won') currentStreak++;
    else break;
  }
  return {
    totalWins: wins.length,
    winRate: history.length > 0 ? Math.round((wins.length / history.length) * 100) : null,
    bestTimeSeconds: times.length > 0 ? Math.min(...times) : null,
    currentStreak,
    avgScore: scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null,
  };
}

function SummaryBanner({ summary, totalGames }) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mb: 1.5 }}>
      <Chip
        size="small"
        variant="outlined"
        icon={<EmojiEventsOutlinedIcon fontSize="small" />}
        label={`${summary.totalWins} Win${summary.totalWins !== 1 ? 's' : ''}`}
      />
      {totalGames >= 3 && <Chip size="small" variant="outlined" label={`${summary.winRate}% Win Rate`} />}
      {summary.bestTimeSeconds !== null && (
        <Chip
          size="small"
          variant="outlined"
          icon={<TimerOutlinedIcon fontSize="small" />}
          label={`Best: ${formatTime(summary.bestTimeSeconds)}`}
        />
      )}
      {summary.currentStreak >= 1 && (
        <Chip
          size="small"
          variant="outlined"
          icon={<LocalFireDepartmentIcon fontSize="small" />}
          label={`Streak: ${summary.currentStreak}`}
        />
      )}
      {summary.avgScore !== null && (
        <Chip
          size="small"
          variant="outlined"
          icon={<StarOutlinedIcon fontSize="small" />}
          label={`Avg Score: ${summary.avgScore}`}
        />
      )}
    </Box>
  );
}

function GameCard({ entry }) {
  const won = entry.outcome === 'won';
  const score = entry.score ?? 0;
  const diffColor = DIFFICULTY_COLOR[entry.difficulty] ?? '#90a4ae';

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.5,
        borderRadius: 2,
        borderLeft: `4px solid ${diffColor}`,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
    >
      {won ? (
        <CheckCircleOutlineIcon color="success" fontSize="small" />
      ) : (
        <CancelOutlinedIcon color="disabled" fontSize="small" />
      )}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" fontWeight={600} noWrap>
          {capitalize(entry.difficulty)}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {formatDate(entry.completedAt)}
        </Typography>
      </Box>
      <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center', flexShrink: 0 }}>
        {won && (
          <Chip
            size="small"
            variant="outlined"
            icon={<TimerOutlinedIcon fontSize="small" />}
            label={formatTime(entry.elapsedSeconds)}
          />
        )}
        {(entry.hintsUsed ?? 0) > 0 && (
          <Chip
            size="small"
            variant="outlined"
            icon={<LightbulbOutlinedIcon fontSize="small" />}
            label={entry.hintsUsed}
          />
        )}
        {won && score > 0 && (
          <Chip size="small" variant="outlined" icon={<StarOutlinedIcon fontSize="small" />} label={score} />
        )}
      </Box>
    </Paper>
  );
}

export default function HistoryView({ navigateBack, history, onRefreshHistory }) {
  const [loading, setLoading] = useState(false);

  const handleRefresh = useCallback(async () => {
    setLoading(true);
    await onRefreshHistory?.();
    setLoading(false);
  }, [onRefreshHistory]);

  const summary = computeSummary(history ?? []);

  return (
    <>
      <AppBar position="sticky" elevation={1}>
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={navigateBack} aria-label="Back">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ ml: 1, flex: 1 }}>
            Puzzle History
          </Typography>
          <IconButton color="inherit" onClick={handleRefresh} disabled={loading} aria-label="Refresh history">
            {loading ? <CircularProgress size={18} color="inherit" /> : <RefreshIcon />}
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box sx={{ maxWidth: 600, mx: 'auto', p: 2 }}>
        {(history ?? []).length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
            No games played yet.
          </Typography>
        ) : (
          <>
            <SummaryBanner summary={summary} totalGames={history.length} />
            <Divider sx={{ mb: 1.5 }} />
            <Stack spacing={1}>
              {history.map((entry, idx) => (
                <GameCard key={entry.id ?? idx} entry={entry} />
              ))}
            </Stack>
          </>
        )}
      </Box>
    </>
  );
}
