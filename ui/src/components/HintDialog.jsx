import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import TutorialModal from './TutorialModal.jsx';

function stageText(hint, stage) {
  if (!hint) return '';
  if (stage === 'nudge') return hint.nudge;
  if (stage === 'focus') return hint.focus;
  return hint.reveal;
}

export default function HintDialog({ open, hint, stage, onAdvance, onDismiss, onAlternateHint }) {
  const [tutorialOpen, setTutorialOpen] = useState(false);

  return (
    <>
      <Dialog open={open} onClose={onDismiss} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            Hint — {hint?.techniqueName}
            {hint?.difficulty && <Chip label={hint.difficulty} size="small" />}
          </Box>
        </DialogTitle>
        <DialogContent>
          <Typography>{stageText(hint, stage)}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTutorialOpen(true)}>Read Tutorial</Button>
          {stage === 'nudge' && onAlternateHint && (
            <Button onClick={onAlternateHint}>Try Different Hint</Button>
          )}
          {stage === 'nudge' && (
            <Button variant="contained" onClick={onAdvance}>Next Hint</Button>
          )}
          {stage === 'focus' && (
            <Button variant="contained" onClick={onAdvance}>Show Me</Button>
          )}
          {stage === 'reveal' && (
            <Button variant="contained" onClick={onDismiss}>Got It</Button>
          )}
          <Button onClick={onDismiss}>Close</Button>
        </DialogActions>
      </Dialog>
      <TutorialModal
        open={tutorialOpen}
        slug={hint?.markdownSlug}
        onClose={() => setTutorialOpen(false)}
      />
    </>
  );
}
