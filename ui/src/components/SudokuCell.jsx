import Box from '@mui/material/Box';

// Cell size: fills available space up to a max of ~84px (9×84=756px ≈ 20cm at 96dpi).
// On mobile (xs) the grid fills the viewport width minus a small gutter.
// On desktop (md+) it fills available height minus header + numberpad space.
// The --cell-size CSS variable is set on the grid container (SudokuGrid) so all
// cells inherit it; here we just reference it with a sensible px fallback.
const cellSize = {
  width: 'var(--sudoku-cell-size, 44px)',
  height: 'var(--sudoku-cell-size, 44px)',
};

// Font scales proportionally: ~26% of cell size, clamped to readable range
const fontSize = 'calc(var(--sudoku-cell-size, 44px) * 0.58)';

function getBackground(isError, isHighlight, isNumberHighlight, isSelected, isRegionHighlight, isGiven) {
  if (isError) return 'error.light';
  if (isHighlight) return 'warning.light';
  if (isNumberHighlight) return 'primary.light';
  if (isSelected) return 'primary.dark';
  if (isRegionHighlight) return '#e8eaf6';
  if (isGiven) return '#fffde7';
  return 'background.paper';
}

// Backgrounds that are always light-coloured regardless of colour mode.
// Candidate text on these needs a dark colour to maintain WCAG AA contrast.
const LIGHT_BG_TOKENS = new Set(['#e8eaf6', '#fffde7', 'error.light', 'warning.light', 'primary.light']);

function getCandidateColor(bg, isSelected, isError, isHighlight) {
  if (isSelected && !isError && !isHighlight) return 'white';
  if (LIGHT_BG_TOKENS.has(bg)) return 'rgba(0, 0, 0, 0.7)';
  return 'text.primary';
}

function CandidateDisplay({ candidates, color }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        width: '100%',
        height: '100%',
        p: '1px',
      }}
    >
      {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
        <Box
          key={n}
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 'calc(var(--sudoku-cell-size, 44px) * 0.22)',
            lineHeight: 1,
            color: candidates.includes(n) ? color : 'transparent',
            fontWeight: 'bold',
          }}
        >
          {n}
        </Box>
      ))}
    </Box>
  );
}

export default function SudokuCell({
  row,
  col,
  value,
  isGiven,
  isError,
  isHighlight,
  isSelected = false,
  isRegionHighlight = false,
  isNumberHighlight = false,
  candidates = [],
  onClick,
}) {
  const bg = getBackground(isError, isHighlight, isNumberHighlight, isSelected, isRegionHighlight, isGiven);
  const hasCandidates = candidates.length > 0;
  const displayValue = value === 0 ? '' : String(value);
  const candidateColor = getCandidateColor(bg, isSelected, isError, isHighlight);

  return (
    <Box
      data-testid={`cell-${row}-${col}`}
      onClick={onClick}
      sx={{
        ...cellSize,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: bg,
        fontWeight: isGiven ? 'bold' : 'normal',
        fontSize: fontSize,
        color: isSelected && !isError && !isHighlight ? 'white' : 'text.primary',
        cursor: 'pointer',
        userSelect: 'none',
        '&:hover': { filter: 'brightness(0.92)' },
      }}
    >
      {hasCandidates ? <CandidateDisplay candidates={candidates} color={candidateColor} /> : displayValue}
    </Box>
  );
}
