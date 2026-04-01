import Box from '@mui/material/Box';
import SudokuCell from './SudokuCell.jsx';

function getBorderSx(row, col) {
  return {
    borderRight: col === 2 || col === 5 ? '2px solid' : '0.5px solid',
    borderBottom: row === 2 || row === 5 ? '2px solid' : '0.5px solid',
    borderLeft: col === 0 ? '2px solid' : 'none',
    borderTop: row === 0 ? '2px solid' : 'none',
    borderColor: 'text.primary',
  };
}

function isSameRegion(selectedCell, row, col) {
  if (!selectedCell) return false;
  const { row: sr, col: sc } = selectedCell;
  return row === sr || col === sc ||
    (Math.floor(row / 3) === Math.floor(sr / 3) &&
     Math.floor(col / 3) === Math.floor(sc / 3));
}

export default function SudokuGrid({ originalGrid, currentGrid, candidateGrid, errorCells, highlightCells = [], selectedCell, selectedNumber, onCellClick }) {
  if (!originalGrid || !currentGrid) return null;

  const selectedValue = selectedCell ? currentGrid[selectedCell.row][selectedCell.col] : 0;
  const highlightSet = new Set(highlightCells.map(({ row, col }) => `${row},${col}`));

  return (
    <Box sx={{
      display: 'inline-block',
      // On mobile: fill viewport width minus a small gutter (8px each side)
      // On desktop (md+): fill height minus header (~56px) minus numberpad (~220px) minus gaps (~32px)
      // Cap at 756px (≈ 20cm at 96dpi). The CSS min() picks the smaller of the two constraints.
      '--sudoku-cell-size': {
        xs: 'min(calc((100vw - 16px) / 9), 84px)',
        md: 'min(calc((100vh - 308px) / 9), 84px)',
      },
    }}>
      {currentGrid.map((rowArr, row) => (
        <Box key={row} sx={{ display: 'flex' }}>
          {rowArr.map((value, col) => (
            <Box key={col} sx={getBorderSx(row, col)}>
              <SudokuCell
                row={row}
                col={col}
                value={value}
                isGiven={originalGrid[row][col] !== 0}
                isError={errorCells.has(`${row},${col}`)}
                isHighlight={highlightSet.has(`${row},${col}`)}
                isSelected={
                  selectedCell?.row === row &&
                  selectedCell?.col === col &&
                  value === 0 &&
                  originalGrid[row][col] === 0 &&
                  selectedNumber === null
                }
                isRegionHighlight={isSameRegion(selectedCell, row, col)}
                isNumberHighlight={selectedValue !== 0 && value === selectedValue}
                candidates={candidateGrid ? candidateGrid[row][col] : []}
                onClick={() => onCellClick(row, col)}
              />
            </Box>
          ))}
        </Box>
      ))}
    </Box>
  );
}
