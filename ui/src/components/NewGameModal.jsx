import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import RadioGroup from '@mui/material/RadioGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import Radio from '@mui/material/Radio';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';

const DIFFICULTIES = ['easy', 'medium', 'hard'];

export default function NewGameModal({ open, defaultDifficulty, isLoading, onConfirm, onCancel }) {
  const [selected, setSelected] = useState(defaultDifficulty ?? 'easy');

  const handleConfirm = () => {
    onConfirm(selected);
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>New Game</DialogTitle>
      <DialogContent>
        <RadioGroup
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
        >
          {DIFFICULTIES.map((d) => (
            <FormControlLabel
              key={d}
              value={d}
              control={<Radio />}
              label={d.charAt(0).toUpperCase() + d.slice(1)}
              disabled={isLoading}
            />
          ))}
        </RadioGroup>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Box sx={{ position: 'relative', display: 'inline-flex' }}>
          <Button
            variant="contained"
            onClick={handleConfirm}
            disabled={isLoading}
          >
            Start
          </Button>
          {isLoading && (
            <CircularProgress
              size={24}
              sx={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                mt: '-12px',
                ml: '-12px',
              }}
            />
          )}
        </Box>
      </DialogActions>
    </Dialog>
  );
}
