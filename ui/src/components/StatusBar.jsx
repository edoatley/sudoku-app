import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';

const SEVERITY = {
  solved: 'success',
  valid: 'success',
  invalid: 'warning',
  error: 'error',
};

export default function StatusBar({ gameStatus, statusMessage, onClose }) {
  const open = gameStatus !== 'idle' && gameStatus !== 'solved' && gameStatus !== 'valid' && gameStatus !== 'invalid' && gameStatus in SEVERITY;
  const severity = SEVERITY[gameStatus] ?? 'info';
  const message = gameStatus === 'solved' ? 'Congratulations — puzzle solved!' : statusMessage;

  return (
    <Snackbar
      open={open}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      sx={{ bottom: { xs: 16, sm: 24 } }}
    >
      <Alert data-testid="status-alert" severity={severity} onClose={onClose} sx={{ width: '100%' }}>
        {message}
      </Alert>
    </Snackbar>
  );
}
