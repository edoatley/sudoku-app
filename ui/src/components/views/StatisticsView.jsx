// @spec NAV-LAYOUT-001, NAV-LAYOUT-002
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Table from '@mui/material/Table';
import TableHead from '@mui/material/TableHead';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function capitalize(str) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function computeStats(history) {
  const difficulties = ['easy', 'medium', 'hard', 'imported'];
  return difficulties
    .map((diff) => {
      const entries = history.filter((e) => e.difficulty === diff);
      const wins = entries.filter((e) => e.outcome === 'won');
      const losses = entries.length - wins.length;
      const avgSeconds =
        wins.length > 0 ? Math.round(wins.reduce((sum, e) => sum + e.elapsedSeconds, 0) / wins.length) : null;
      const scores = wins.map((e) => e.score ?? 0).filter((s) => s > 0);
      const avgScore = scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null;
      return { difficulty: diff, total: entries.length, wins: wins.length, losses, avgSeconds, avgScore };
    })
    .filter((row) => row.total > 0);
}

export default function StatisticsView({ navigateBack, history }) {
  const rows = computeStats(history ?? []);

  return (
    <>
      <AppBar position="sticky" elevation={1}>
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={navigateBack} aria-label="Back">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ ml: 1, flex: 1 }}>
            Statistics
          </Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ maxWidth: 600, mx: 'auto', p: 2 }}>
        {rows.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
            No games played yet.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Difficulty</TableCell>
                <TableCell align="right">Games</TableCell>
                <TableCell align="right">Wins</TableCell>
                <TableCell align="right">Losses</TableCell>
                <TableCell align="right">Avg Time</TableCell>
                <TableCell align="right">Avg Score</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.difficulty}>
                  <TableCell>{capitalize(row.difficulty)}</TableCell>
                  <TableCell align="right">{row.total}</TableCell>
                  <TableCell align="right">{row.wins}</TableCell>
                  <TableCell align="right">{row.losses}</TableCell>
                  <TableCell align="right">{row.avgSeconds !== null ? formatTime(row.avgSeconds) : '—'}</TableCell>
                  <TableCell align="right">{row.avgScore !== null ? row.avgScore : '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Box>
    </>
  );
}
