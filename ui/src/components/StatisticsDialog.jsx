import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableHead from '@mui/material/TableHead';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Typography from '@mui/material/Typography';

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
  return difficulties.map((diff) => {
    const entries = history.filter((e) => e.difficulty === diff);
    const wins = entries.filter((e) => e.outcome === 'won');
    const losses = entries.length - wins.length;
    const avgSeconds = wins.length > 0
      ? Math.round(wins.reduce((sum, e) => sum + e.elapsedSeconds, 0) / wins.length)
      : null;
    return { difficulty: diff, total: entries.length, wins: wins.length, losses, avgSeconds };
  }).filter((row) => row.total > 0);
}

export default function StatisticsDialog({ open, history, onClose }) {
  const rows = computeStats(history);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Statistics</DialogTitle>
      <DialogContent>
        {rows.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 2 }}>
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
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.difficulty}>
                  <TableCell>{capitalize(row.difficulty)}</TableCell>
                  <TableCell align="right">{row.total}</TableCell>
                  <TableCell align="right">{row.wins}</TableCell>
                  <TableCell align="right">{row.losses}</TableCell>
                  <TableCell align="right">
                    {row.avgSeconds !== null ? formatTime(row.avgSeconds) : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
