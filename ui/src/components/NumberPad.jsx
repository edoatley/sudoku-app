import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import Collapse from '@mui/material/Collapse';
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
import EditIcon from '@mui/icons-material/Edit';
import EditNoteIcon from '@mui/icons-material/EditNote';
import HelpIcon from '@mui/icons-material/Help';

const btnSx = { flex: 1, minWidth: 0, height: { xs: 44, sm: 52 }, p: 0, fontSize: '1.15rem' };

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

// @spec FE-UI-006 — disable a digit button in normal mode once that digit appears 9 times
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
      <span style={{ display: 'inline-flex' }}>
        <Button
          aria-label={label}
          variant={active ? 'contained' : 'outlined'}
          color={active ? 'primary' : 'inherit'}
          onClick={onClick}
          disabled={disabled}
          sx={
            active
              ? {
                  ...toolBtnSx,
                  color: 'primary.contrastText',
                  borderColor: 'primary.main',
                  bgcolor: 'primary.main',
                  '&:hover': { bgcolor: 'primary.dark' },
                }
              : toolBtnSx
          }
        >
          {icon}
          <Typography
            variant="caption"
            sx={{ fontSize: '0.6rem', textTransform: 'uppercase', letterSpacing: 0.3, lineHeight: 1 }}
          >
            {label}
          </Typography>
        </Button>
      </span>
    </Tooltip>
  );
}

// ── Composable sub-components ────────────────────────────────────────────────

/**
 * Toolbar row: mode toggle left | Undo, Clear | Check, Hint, Fill, Help right
 * @spec FE-MOB-001 — action buttons stay in a single toolbar row at all viewport sizes
 */
export function NumberPadToolbar({
  inputMode,
  onModeChange,
  onClearCell,
  onUndo,
  canUndo,
  onValidate,
  onHint,
  onFillCandidates,
  isLoading,
  onHelp,
}) {
  const isNormal = inputMode === 'normal';
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
      {/* Mode toggle — left.
          xs: compact single-button showing current mode; tap cycles to the other.
          sm+: full two-segment toggle group. */}
      <Tooltip title="Toggle mode (Space)" arrow>
        <Box>
          {/* Compact single-state button for mobile */}
          <Button
            variant="contained"
            size="small"
            onClick={() => onModeChange(isNormal ? 'candidate' : 'normal')}
            startIcon={isNormal ? <EditIcon sx={{ fontSize: 15 }} /> : <EditNoteIcon sx={{ fontSize: 15 }} />}
            sx={{
              display: { xs: 'inline-flex', sm: 'none' },
              bgcolor: isNormal ? 'primary.main' : 'success.main',
              '&:hover': { bgcolor: isNormal ? 'primary.dark' : 'success.dark' },
              px: 1,
              py: 0.5,
              fontSize: '0.75rem',
              whiteSpace: 'nowrap',
              minWidth: 0,
            }}
          >
            {isNormal ? 'Normal' : 'Candidate'}
          </Button>
          {/* Full two-segment toggle for sm+ */}
          <ToggleButtonGroup
            value={inputMode}
            exclusive
            onChange={(_, val) => {
              if (val) onModeChange(val);
            }}
            size="small"
            sx={{ display: { xs: 'none', sm: 'flex' } }}
          >
            <ToggleButton
              value="normal"
              sx={{
                px: 1.5,
                py: 0.5,
                fontSize: '0.75rem',
                gap: 0.5,
                '&.Mui-selected': {
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  '&:hover': { bgcolor: 'primary.dark' },
                },
              }}
            >
              <EditIcon sx={{ fontSize: 15 }} />
              Normal
            </ToggleButton>
            <ToggleButton
              value="candidate"
              sx={{
                px: 1.5,
                py: 0.5,
                fontSize: '0.75rem',
                gap: 0.5,
                '&.Mui-selected': {
                  bgcolor: 'success.main',
                  color: 'success.contrastText',
                  '&:hover': { bgcolor: 'success.dark' },
                },
              }}
            >
              <EditNoteIcon sx={{ fontSize: 15 }} />
              Candidate
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>
      </Tooltip>

      {/* Action buttons — right */}
      <Box sx={{ display: 'flex', gap: { xs: 0.5, sm: 1 } }}>
        <ButtonGroup variant="outlined" color="inherit" size="small">
          <ToolButton
            label="Undo"
            tooltip="Undo last move (U)"
            icon={<UndoIcon sx={{ fontSize: 20 }} />}
            onClick={onUndo}
            disabled={!canUndo}
          />
          <ToolButton
            label="Clear"
            tooltip="Clear cell (Del or 0)"
            icon={<ClearIcon sx={{ fontSize: 20 }} />}
            onClick={onClearCell}
          />
        </ButtonGroup>
        <ButtonGroup variant="outlined" color="inherit" size="small">
          <ToolButton
            label="Check"
            tooltip="Validate puzzle (C)"
            icon={<FactCheckIcon sx={{ fontSize: 20 }} />}
            onClick={onValidate}
            disabled={isLoading}
          />
          <ToolButton
            label="Hint"
            tooltip="Get a hint (H)"
            icon={<LightbulbIcon sx={{ fontSize: 20 }} />}
            onClick={onHint}
            disabled={isLoading}
          />
          <ToolButton
            label="Fill"
            tooltip="Fetch and fill in all valid candidates (F)"
            icon={<LibraryAddIcon sx={{ fontSize: 20 }} />}
            onClick={onFillCandidates}
            disabled={isLoading}
          />
          <ToolButton
            label="Help"
            tooltip="Controls &amp; keyboard shortcuts (? or /)"
            icon={<HelpIcon sx={{ fontSize: 20 }} />}
            onClick={onHelp}
          />
        </ButtonGroup>
      </Box>
    </Box>
  );
}

/**
 * Single row of number buttons 1–9 (sm+); 5+4 split on xs
 * @spec FE-MOB-002 — digit buttons 1-9 in one row above `sm`, two rows (1-5, 6-9) below it
 */
export function NumberPadInput({ selectedNumber, inputMode, onNumberSelect, completedNumbers }) {
  const nums = [1, 2, 3, 4, 5, 6, 7, 8, 9];
  const numProps = (n) => ({ n, selectedNumber, onNumberSelect, completedNumbers, inputMode });
  return (
    <Box data-testid="numberpad-normal">
      {/* Single row on sm+ */}
      <Box sx={{ display: { xs: 'none', sm: 'flex' }, gap: 0.75 }}>
        {nums.map((n) => (
          <NumButton key={n} {...numProps(n)} />
        ))}
      </Box>
      {/* Two rows on xs: 1–5 then 6–9 */}
      <Box sx={{ display: { xs: 'flex', sm: 'none' }, flexDirection: 'column', gap: 0.5 }}>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <NumButton key={n} {...numProps(n)} />
          ))}
        </Box>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[6, 7, 8, 9].map((n) => (
            <NumButton key={n} {...numProps(n)} />
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
export default function NumberPad({
  selectedNumber,
  inputMode,
  onNumberSelect,
  onModeChange,
  onClearCell,
  onUndo,
  canUndo,
  onValidate,
  onHint,
  onFillCandidates,
  isLoading,
  completedNumbers,
  gameStatus,
  statusMessage,
  onCloseStatus,
}) {
  return (
    <Stack spacing={1} alignItems="center">
      <NumberPadToolbar
        inputMode={inputMode}
        onModeChange={onModeChange}
        onClearCell={onClearCell}
        onUndo={onUndo}
        canUndo={canUndo}
        onValidate={onValidate}
        onHint={onHint}
        onFillCandidates={onFillCandidates}
        isLoading={isLoading}
      />
      <NumberPadInput
        selectedNumber={selectedNumber}
        inputMode={inputMode}
        onNumberSelect={onNumberSelect}
        completedNumbers={completedNumbers}
      />
      <NumberPadStatus gameStatus={gameStatus} statusMessage={statusMessage} onCloseStatus={onCloseStatus} />
    </Stack>
  );
}
