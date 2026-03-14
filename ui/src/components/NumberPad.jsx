import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ClearIcon from '@mui/icons-material/Clear';

const btnSx = { minWidth: { xs: 36, sm: 48 }, height: { xs: 36, sm: 48 }, p: 0 };

function NumButton({ n, selectedNumber, onNumberSelect }) {
  const active = selectedNumber === n;
  return (
    <Button
      variant={active ? 'contained' : 'outlined'}
      onClick={() => onNumberSelect(active ? null : n)}
      sx={btnSx}
    >
      {n}
    </Button>
  );
}

export default function NumberPad({ selectedNumber, inputMode, onNumberSelect, onModeChange }) {
  return (
    <Stack spacing={1} alignItems="center">
      <ToggleButtonGroup
        value={inputMode}
        exclusive
        onChange={(_, val) => { if (val) onModeChange(val); }}
        size="small"
      >
        <ToggleButton value="normal">Normal</ToggleButton>
        <ToggleButton value="candidate">Candidate</ToggleButton>
      </ToggleButtonGroup>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, alignItems: 'flex-start' }}>
        {/* Row 1: 1–5 */}
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} />
          ))}
        </Box>
        {/* Row 2: 6–9 + clear */}
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[6, 7, 8, 9].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} />
          ))}
          <Button
            variant="outlined"
            onClick={() => onNumberSelect(null)}
            sx={{ ...btnSx, color: 'error.main', borderColor: 'error.main', '&:hover': { borderColor: 'error.dark', bgcolor: 'error.50' } }}
          >
            <ClearIcon fontSize="small" />
          </Button>
        </Box>
      </Box>
    </Stack>
  );
}
