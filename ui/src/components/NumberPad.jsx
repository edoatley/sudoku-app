import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import Collapse from '@mui/material/Collapse';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ClearIcon from '@mui/icons-material/Clear';
import UndoIcon from '@mui/icons-material/Undo';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import LibraryAddIcon from '@mui/icons-material/LibraryAdd';

// @spec FE-UI-006, FE-MOB-002, FE-MOB-003
const numBtnSx = {
  flex: 1,
  minWidth: 0,
  height: { xs: 48, sm: 52 },
  p: 0,
  fontSize: { xs: '1.1rem', sm: '1.15rem' },
};

// Smaller buttons for the candidate row
const candBtnSx = {
  flex: 1,
  minWidth: 0,
  height: { xs: 36, sm: 40 },
  p: 0,
  fontSize: { xs: '0.85rem', sm: '0.9rem' },
};

const toolBtnSx = {
  minWidth: 0,
  width: { xs: 44, sm: 52 },
  flexDirection: 'column',
  height: 'auto',
  py: 0.75,
  px: 0.5,
  gap: 0.25,
  color: 'text.secondary',
  '&:hover': { color: 'text.primary', bgcolor: 'action.hover' },
  '&.Mui-disabled': { color: 'action.disabled' },
};

// @spec FE-UI-005, FE-MOB-003
function CandidateNumButton({ n, selectedNumber, onNumberSelect, inputMode }) {
  const active = inputMode === 'candidate' && selectedNumber === n;
  return (
    <Button
      variant={active ? 'contained' : 'outlined'}
      color="secondary"
      onClick={() => {
        onNumberSelect(active ? null : n, 'candidate');
      }}
      sx={candBtnSx}
    >
      {n}
    </Button>
  );
}

// @spec FE-UI-004, FE-UI-006, FE-MOB-002
function NumButton({ n, selectedNumber, onNumberSelect, completedNumbers, inputMode }) {
  const active = inputMode === 'normal' && selectedNumber === n;
  const completed = inputMode === 'normal' && completedNumbers?.has(n);
  return (
    <Button
      variant={active ? 'contained' : 'outlined'}
      onClick={() => onNumberSelect(active ? null : n, 'normal')}
      disabled={completed}
      sx={numBtnSx}
    >
      {n}
    </Button>
  );
}

function ToolButton({ label, icon, tooltip, onClick, disabled, active }) {
  return (
    <Tooltip title={tooltip} arrow>
      <span style={{ display: 'inline-flex' }}>
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

// ── Composable sub-components ────────────────────────────────────────────────

/** Toolbar row: action buttons only — Undo, Clear | Check, Hint, Fill */
// @spec FE-UI-008, FE-MOB-001
export function NumberPadToolbar({ onClearCell, onUndo, canUndo, onValidate, onHint, onFillCandidates, isLoading }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 1 }}>
      <ButtonGroup variant="outlined" color="inherit" size="small">
        <ToolButton label="Undo" tooltip="Undo last move" icon={<UndoIcon sx={{ fontSize: 20 }} />} onClick={onUndo} disabled={!canUndo} />
        <ToolButton label="Clear" tooltip="Clear selected cell" icon={<ClearIcon sx={{ fontSize: 20 }} />} onClick={onClearCell} />
      </ButtonGroup>
      <ButtonGroup variant="outlined" color="inherit" size="small">
        <ToolButton label="Check" tooltip="Validate puzzle" icon={<FactCheckIcon sx={{ fontSize: 20 }} />} onClick={onValidate} disabled={isLoading} />
        <ToolButton label="Hint" tooltip="Get a hint" icon={<LightbulbIcon sx={{ fontSize: 20 }} />} onClick={onHint} disabled={isLoading} />
        <ToolButton label="Fill" tooltip="Fetch and fill in all valid candidates" icon={<LibraryAddIcon sx={{ fontSize: 20 }} />} onClick={onFillCandidates} disabled={isLoading} />
      </ButtonGroup>
    </Box>
  );
}

/**
 * Candidate digit row (1–9), always shown above the normal number row.
 * Tapping a candidate button also switches inputMode to 'candidate'.
 * @spec FE-UI-005, FE-MOB-003
 */
export function CandidateRow({ selectedNumber, inputMode, onNumberSelect }) {
  return (
    <Box>
      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', fontSize: '0.65rem', textTransform: 'uppercase', letterSpacing: 0.5, mb: 0.25 }}>
        Candidate
      </Typography>
      <Box sx={{ display: 'flex', gap: 0.5 }}>
        {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
          <CandidateNumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} inputMode={inputMode} />
        ))}
      </Box>
    </Box>
  );
}

/**
 * Normal digit row(s) 1–9.
 * On mobile (xs): splits into two rows — 1–5 then 6–9.
 * On sm+: single row.
 * @spec FE-UI-004, FE-UI-006, FE-MOB-002
 */
export function NumberPadInput({ selectedNumber, inputMode, onNumberSelect, completedNumbers }) {
  const nums = [1, 2, 3, 4, 5, 6, 7, 8, 9];
  return (
    <Box>
      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', fontSize: '0.65rem', textTransform: 'uppercase', letterSpacing: 0.5, mb: 0.25 }}>
        Normal
      </Typography>
      {/* Single row on sm+; two rows on xs */}
      <Box sx={{ display: { xs: 'none', sm: 'flex' }, gap: 0.75 }}>
        {nums.map((n) => (
          <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
        ))}
      </Box>
      <Box sx={{ display: { xs: 'flex', sm: 'none' }, flexDirection: 'column', gap: 0.5 }}>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
          ))}
        </Box>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[6, 7, 8, 9].map((n) => (
            <NumButton key={n} n={n} selectedNumber={selectedNumber} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} inputMode={inputMode} />
          ))}
        </Box>
      </Box>
    </Box>
  );
}

/** Status alert (valid / invalid feedback) */
export function NumberPadStatus({ gameStatus, statusMessage, onCloseStatus }) {
  return (
    <Collapse in={gameStatus === 'valid' || gameStatus === 'invalid'} unmountOnExit sx={{ width: '100%' }}>
      <Alert
        data-testid="status-alert"
        severity={gameStatus === 'invalid' ? 'warning' : 'success'}
        onClose={onCloseStatus}
        sx={{ py: 1, px: 2, fontWeight: 'bold', letterSpacing: 0.4 }}
      >
        {statusMessage}
      </Alert>
    </Collapse>
  );
}

/** Legacy all-in-one component (kept for tests / fallback) */
export default function NumberPad({ selectedNumber, inputMode, onNumberSelect, onModeChange, onClearCell, onUndo, canUndo, onValidate, onHint, onFillCandidates, isLoading, completedNumbers, gameStatus, statusMessage, onCloseStatus }) {
  return (
    <Stack spacing={1} alignItems="stretch">
      <NumberPadToolbar onModeChange={onModeChange} onClearCell={onClearCell} onUndo={onUndo} canUndo={canUndo} onValidate={onValidate} onHint={onHint} onFillCandidates={onFillCandidates} isLoading={isLoading} />
      <CandidateRow selectedNumber={selectedNumber} inputMode={inputMode} onNumberSelect={onNumberSelect} />
      <NumberPadInput selectedNumber={selectedNumber} inputMode={inputMode} onNumberSelect={onNumberSelect} completedNumbers={completedNumbers} />
      <NumberPadStatus gameStatus={gameStatus} statusMessage={statusMessage} onCloseStatus={onCloseStatus} />
    </Stack>
  );
}
