import Box from '@mui/material/Box';

const cellSize = { width: { xs: 32, sm: 44 }, height: { xs: 32, sm: 44 } };
const fontSize = { xs: '0.875rem', sm: '1.125rem' };

function getBackground(isError, isHint, isGiven) {
  if (isError) return 'error.light';
  if (isHint) return 'info.light';
  if (isGiven) return 'grey.100';
  return 'background.paper';
}

function CandidateDisplay({ candidates }) {
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
            fontSize: { xs: '0.45rem', sm: '0.6rem' },
            lineHeight: 1,
            color: candidates.includes(n) ? 'primary.main' : 'transparent',
            fontWeight: 'bold',
          }}
        >
          {n}
        </Box>
      ))}
    </Box>
  );
}

export default function SudokuCell({ value, isGiven, isError, isHint, candidates = [], onClick }) {
  const bg = getBackground(isError, isHint, isGiven);
  const hasCandidates = candidates.length > 0;
  const displayValue = value === 0 ? '' : String(value);

  return (
    <Box
      onClick={isGiven ? undefined : onClick}
      sx={{
        ...cellSize,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: bg,
        fontWeight: isGiven ? 'bold' : 'normal',
        fontSize,
        cursor: isGiven ? 'default' : 'pointer',
        userSelect: 'none',
        '&:hover': isGiven ? {} : { bgcolor: 'action.hover' },
      }}
    >
      {hasCandidates ? (
        <CandidateDisplay candidates={candidates} />
      ) : (
        displayValue
      )}
    </Box>
  );
}
