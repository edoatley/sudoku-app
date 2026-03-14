import Collapse from '@mui/material/Collapse';
import Alert from '@mui/material/Alert';

const SEVERITY = {
  solved: 'success',
  valid: 'success',
  invalid: 'warning',
  error: 'error',
};

export default function StatusBar({ gameStatus, statusMessage, onClose }) {
  const open = gameStatus !== 'idle' && gameStatus in SEVERITY;
  const severity = SEVERITY[gameStatus] ?? 'info';
  const message = gameStatus === 'solved' ? 'Congratulations — puzzle solved!' : statusMessage;

  return (
    <Collapse in={open}>
      <Alert severity={severity} onClose={onClose} sx={{ width: '100%' }}>
        {message}
      </Alert>
    </Collapse>
  );
}
