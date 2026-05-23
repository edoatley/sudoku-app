// @spec FE-UI-042, FE-UI-042a, FE-UI-042b
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Typography from '@mui/material/Typography';
import CheckCircleOutlineIcon  from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlinedIcon      from '@mui/icons-material/CancelOutlined';
import TimerOutlinedIcon       from '@mui/icons-material/TimerOutlined';
import LightbulbOutlinedIcon   from '@mui/icons-material/LightbulbOutlined';
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import StarOutlinedIcon        from '@mui/icons-material/StarOutlined';

const DIFFICULTY_COLOR = {
  easy:     '#4caf50',
  medium:   '#ff9800',
  hard:     '#f44336',
  imported: '#9c27b0',
};

// TODO: replace with server-side scoring system
const DIFFICULTY_BASE_SCORE = { easy: 100, medium: 200, hard: 350, imported: 200 };

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

function calculateScore(entry) {
  if (entry.outcome !== 'won') return 0;
  const base = DIFFICULTY_BASE_SCORE[entry.difficulty] ?? 100;
  const timeBonus = Math.max(0, base - Math.floor((entry.elapsedSeconds ?? 0) / 10));
  const hintMultiplier = Math.max(0, 1 - 0.1 * (entry.hintsUsed ?? 0));
  return Math.round(timeBonus * hintMultiplier);
}

function computeSummary(history) {
  const wins = history.filter(e => e.outcome === 'won');
  const times = wins.map(e => e.elapsedSeconds).filter(n => typeof n === 'number');
  const scores = wins.map(calculateScore).filter(s => s > 0);
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
  const chips = [];

  chips.push(
    <Chip
      key="wins"
      size="small"
      variant="outlined"
      icon={<EmojiEventsOutlinedIcon fontSize="small" />}
      label={`${summary.totalWins} Win${summary.totalWins !== 1 ? 's' : ''}`}
    />
  );

  if (totalGames >= 3) {
    chips.push(
      <Chip
        key="rate"
        size="small"
        variant="outlined"
        label={`${summary.winRate}% Win Rate`}
      />
    );
  }

  if (summary.bestTimeSeconds !== null) {
    chips.push(
      <Chip
        key="best"
        size="small"
        variant="outlined"
        icon={<TimerOutlinedIcon fontSize="small" />}
        label={`Best: ${formatTime(summary.bestTimeSeconds)}`}
      />
    );
  }

  if (summary.currentStreak >= 1) {
    chips.push(
      <Chip
        key="streak"
        size="small"
        variant="outlined"
        icon={<LocalFireDepartmentIcon fontSize="small" />}
        label={`Streak: ${summary.currentStreak}`}
      />
    );
  }

  if (summary.avgScore !== null) {
    chips.push(
      <Chip
        key="score"
        size="small"
        variant="outlined"
        icon={<StarOutlinedIcon fontSize="small" />}
        label={`Avg Score: ${summary.avgScore}`}
      />
    );
  }

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mb: 1.5 }}>
      {chips}
    </Box>
  );
}

function GameCard({ entry }) {
  const won = entry.outcome === 'won';
  const score = calculateScore(entry);
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
          <Chip
            size="small"
            variant="outlined"
            icon={<StarOutlinedIcon fontSize="small" />}
            label={score}
          />
        )}
      </Box>
    </Paper>
  );
}

export default function PuzzleHistoryDialog({ open, history, onClose }) {
  const summary = computeSummary(history);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Puzzle History</DialogTitle>
      <DialogContent sx={{ pt: 1 }}>
        {history.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 2 }}>
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
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
