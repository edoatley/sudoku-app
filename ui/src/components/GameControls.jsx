import Button from '@mui/material/Button';

export default function GameControls({ onOpenNewGame }) {
  return (
    <Button variant="contained" onClick={onOpenNewGame}>
      New Game
    </Button>
  );
}
