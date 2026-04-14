import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ClearIcon from '@mui/icons-material/Clear';
import UndoIcon from '@mui/icons-material/Undo';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import LibraryAddIcon from '@mui/icons-material/LibraryAdd';

const btnSx = { minWidth: { xs: 44, sm: 56 }, height: { xs: 44, sm: 56 }, p: 0, fontSize: '1.15rem' };

const toolBtnSx = {
  minWidth: 0,
  width: { xs: 44, sm: 52 },
  flexDirection: 'column',
  height: 'auto',
  py: 0.75,
  px: 0.5,
  gap: 0.25,
  color: 'text.secondary',
  borderColor: 'divider',
  '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'action.hover' },
  '&.Mui-disabled': { borderColor: 'divider', color: 'action.disabled' },
};

function NumButton({ n, selectedNumber, onNumberSelect, completedNumbers, inputMode }) {
  const active = selectedNumber === n;
  const completed = inputMode === 'normal' && completedNumbers?.has(n);
  return (
    <Button
      variant={active ? 'contained' : 'outlined'}
      onClick={() => onNumberSelect(active ? null : n)}
      disabled={completed}
      sx={btnSx}
    >
      {n}
    </Button>
  );
}

function ToolButton({ label, icon, tooltip, onClick, disabled, active }) {
  return (
    <Tooltip title={tooltip} arrow>
      <span>
        <Button
          aria-label={label}
          variant={active ? 'contained' : 'outlined'}
          color={active ? 'primary' : 'inherit'}
          onClick={onClick}
          disabled={disabled}
          sx={active ? { ...toolBtnSx, color: 'primary.contrastText', borderColor: 'primary.main', bgcolor: 'primary.main', '&:hover': { bgcolor: 'primary.dark' } } : toolBtnSx}
        >
          {icon}
          <Typography variant="caption" lineHeight={1} sx={{ fontSize: '0.6rem', textTransform: 'uppercase', letterSpacing: 0.3 }}>
            {label}
          </Typography>
        </Button>
      </span>
    </Tooltip>
  );
}

export default function NumberPad({ selectedNumber, inputMode, onNumberSelect, onModeChange, onClearCell, onUndo, canUndo, onValidate, onHint, onFillCandidates, isLoading, completedNumbers, gameStatus, statusMessage, onCloseStatus }) {
  return (
    <Stack spacing={1} alignItems="center">
      {/* Numbers: 1–5 top row, 6–9 bottom row */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
        <Box sx={{ display: 'flex', gap: 0.75 }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
          ))}
        </Box>
        <Box sx={{ display: 'flex', gap: 0.75, justifyContent: 'center' }}>
          {[6, 7, 8, 9].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
          ))}
        </Box>
      </Box>

      {/* Mode toggle below numbers */}
      <ToggleButtonGroup
        value={inputMode}
        exclusive
        onChange={(_, val) => { if (val) onModeChange(val); }}
        size="small"
      >
        <ToggleButton value="normal">Normal</ToggleButton>
        <ToggleButton value="candidate">Candidate</ToggleButton>
      </ToggleButtonGroup>

      {/* 3×3 number grid */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        {[[1, 2, 3], [4, 5, 6], [7, 8, 9]].map((row) => (
          <Box key={row[0]} sx={{ display: 'flex', gap: 0.5 }}>
            {row.map((n) => (
              <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
            ))}
          </Box>
        ))}
      </Box>

      {/* Toolbar: destructive group | tools group */}
      <Box sx={{ display: 'flex', alignItems: 'stretch', border: '1px solid', borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
        <ToolButton label="Undo" tooltip="Undo last move" icon={<UndoIcon fontSize="small" />} onClick={onUndo} disabled={!canUndo} />
        <ToolButton label="Clear" tooltip="Clear selected cell" icon={<ClearIcon fontSize="small" />} onClick={onClearCell} />
        <Divider orientation="vertical" flexItem />
        <ToolButton label="Check" tooltip="Validate puzzle" icon={<FactCheckIcon fontSize="small" />} onClick={onValidate} disabled={isLoading} />
        <ToolButton label="Hint" tooltip="Get a hint" icon={<LightbulbIcon fontSize="small" />} onClick={onHint} disabled={isLoading} />
        <ToolButton label="Fill" tooltip="Fetch and fill in all valid candidates" icon={<LibraryAddIcon fontSize="small" />} onClick={onFillCandidates} disabled={isLoading} />
      </Box>

      {/* Inline status for valid/invalid results */}
      <Collapse in={gameStatus === 'valid' || gameStatus === 'invalid'} sx={{ width: '100%' }}>
        <Alert
          data-testid="status-alert"
          severity={gameStatus === 'invalid' ? 'warning' : 'success'}
          onClose={onCloseStatus}
          sx={{ py: 1, px: 2, fontWeight: 'bold', letterSpacing: 0.4 }}
        >
          {statusMessage}
        </Alert>
      </Collapse>
    </Stack>
  );
}
