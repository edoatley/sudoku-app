import Stack from '@mui/material/Stack';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';

const DIFFICULTIES = ['easy', 'medium', 'hard'];

export default function GameControls({ difficulty, isLoading, onDifficultyChange, onNewGame }) {
  return (
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
  );
}
