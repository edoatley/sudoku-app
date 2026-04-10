import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import CloseIcon from '@mui/icons-material/Close';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
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
        <DialogTitle sx={{ m: 0, p: 2, pr: 10 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            Hint — {hint?.techniqueName}
            {hint?.difficulty && <Chip label={hint.difficulty} size="small" />}
          </Box>
          <IconButton
            onClick={() => setTutorialOpen(true)}
            size="small"
            title="Read tutorial"
            sx={{ position: 'absolute', right: 40, top: 8 }}
          >
            <HelpOutlineIcon fontSize="small" />
          </IconButton>
          <IconButton
            onClick={onDismiss}
            size="small"
            title="Close"
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Typography>{stageText(hint, stage)}</Typography>
        </DialogContent>
        <DialogActions>
          {stage === 'nudge' && onAlternateHint && (
            <Button onClick={onAlternateHint}>Try Different Hint</Button>
          )}
          {stage === 'nudge' && (
            <Button variant="contained" onClick={onAdvance}>Show Me</Button>
          )}
          {stage === 'focus' && (
            <Button variant="contained" onClick={onAdvance}>Show Me</Button>
          )}
          {stage === 'reveal' && (
            <Button variant="contained" onClick={onDismiss}>Got It</Button>
          )}
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
