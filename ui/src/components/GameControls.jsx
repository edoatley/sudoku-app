import Stack from '@mui/material/Stack';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';

const DIFFICULTIES = ['easy', 'medium', 'hard'];

export default function GameControls({ difficulty, isLoading, autoNotesActive, onDifficultyChange, onNewGame, onValidate, onHint, onAutoNotes }) {
  return (
    <Stack spacing={1} alignItems="center">
      <Stack direction="row" spacing={1} alignItems="center">
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <InputLabel id="difficulty-label">Difficulty</InputLabel>
          <Select
            labelId="difficulty-label"
            value={difficulty}
            label="Difficulty"
            onChange={(e) => onDifficultyChange(e.target.value)}
            disabled={isLoading}
          >
            {DIFFICULTIES.map((d) => (
              <MenuItem key={d} value={d} sx={{ textTransform: 'capitalize' }}>
                {d.charAt(0).toUpperCase() + d.slice(1)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button variant="contained" onClick={onNewGame} disabled={isLoading}>
          New Game
        </Button>
        {isLoading && <CircularProgress size={24} />}
      </Stack>

      <Stack direction="row" spacing={1} alignItems="center">
        <Button variant="outlined" onClick={onValidate} disabled={isLoading}>
          Validate
        </Button>
        <Button variant="outlined" onClick={onHint} disabled={isLoading}>
          Hint
        </Button>
        <Button
          variant={autoNotesActive ? 'contained' : 'outlined'}
          color={autoNotesActive ? 'secondary' : 'primary'}
          onClick={onAutoNotes}
          disabled={isLoading}
        >
          Auto-Notes
        </Button>
      </Stack>
    </Stack>
  );
}
