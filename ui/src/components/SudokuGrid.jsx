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

export default function SudokuGrid({ originalGrid, currentGrid, candidateGrid, errorCells, hintCell, onCellClick }) {
  if (!originalGrid || !currentGrid) return null;

  return (
    <Box sx={{ display: 'inline-block', overflowX: 'auto' }}>
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
                isHint={hintCell?.row === row && hintCell?.col === col}
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
