import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';

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

export default function PuzzleHistoryDialog({ open, history, onClose }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Puzzle History</DialogTitle>
      <DialogContent>
        {history.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 2 }}>
            No games played yet.
          </Typography>
        ) : (
          <List dense>
            {history.map((entry, idx) => {
              const won = entry.outcome === 'won';
              const primary = `${capitalize(entry.difficulty)} — ${won ? 'Won' : 'Abandoned'}`;
              const secondary = won
                ? `${formatDate(entry.completedAt)} in ${formatTime(entry.elapsedSeconds)}`
                : formatDate(entry.completedAt);
              return (
                <ListItem key={entry.id ?? idx} disableGutters>
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    {won ? (
                      <CheckCircleOutlineIcon color="success" />
                    ) : (
                      <CancelOutlinedIcon color="disabled" />
                    )}
                  </ListItemIcon>
                  <ListItemText primary={primary} secondary={secondary} />
                </ListItem>
              );
            })}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
