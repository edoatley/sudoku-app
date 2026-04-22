import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import CloseIcon from '@mui/icons-material/Close';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import TutorialModal from './TutorialModal.jsx';
import { formatHintText } from '../utils/hintDisplay.js';

// @spec HE-UI-001, HE-UI-002, HE-UI-003, HE-UI-004, HE-UI-005
function stageText(hint, stage) {
  if (!hint) return '';
  if (stage === 'nudge') return formatHintText(hint.nudge);
  if (stage === 'focus') return formatHintText(hint.focus);
  return formatHintText(hint.reveal);
}

function HintActions({ stage, onAdvance, onDismiss, onAlternateHint }) {
  return (
    <>
      {stage === 'nudge' && onAlternateHint && (
        <Button size="small" onClick={onAlternateHint}>Try Different Hint</Button>
      )}
      {(stage === 'nudge' || stage === 'focus') && (
        <Button size="small" variant="contained" onClick={onAdvance}>Show Me</Button>
      )}
      {stage === 'reveal' && (
        <Button size="small" variant="contained" onClick={onDismiss}>Got It</Button>
      )}
    </>
  );
}

export default function HintDialog({ open, hint, stage, onAdvance, onDismiss, onAlternateHint }) {
  const [tutorialOpen, setTutorialOpen] = useState(false);
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  const titleBar = (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
      <Typography variant="subtitle1" fontWeight="bold">
        Hint — {hint?.techniqueName}
      </Typography>
      {hint?.difficulty && <Chip label={hint.difficulty} size="small" />}
      <Box sx={{ ml: 'auto', display: 'flex', gap: 0.5 }}>
        <IconButton onClick={() => setTutorialOpen(true)} size="small" title="Read tutorial">
          <HelpOutlineIcon fontSize="small" />
        </IconButton>
        <IconButton onClick={onDismiss} size="small" title="Close">
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>
    </Box>
  );

  return (
    <>
      {isDesktop ? (
        /* Desktop: inline card below the board, capped to grid width */
        <Collapse in={open}>
          <Paper data-testid="hint-panel" variant="outlined" sx={{ p: 2, borderRadius: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {titleBar}
            <Typography variant="body2">{stageText(hint, stage)}</Typography>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
              <HintActions stage={stage} onAdvance={onAdvance} onDismiss={onDismiss} onAlternateHint={onAlternateHint} />
            </Box>
          </Paper>
        </Collapse>
      ) : (
        /* Mobile: keep the dialog */
        <Dialog data-testid="hint-panel" open={open} onClose={onDismiss} maxWidth="sm" fullWidth>
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
            <HintActions stage={stage} onAdvance={onAdvance} onDismiss={onDismiss} onAlternateHint={onAlternateHint} />
          </DialogActions>
        </Dialog>
      )}
      <TutorialModal
        open={tutorialOpen}
        slug={hint?.markdownSlug}
        onClose={() => setTutorialOpen(false)}
      />
    </>
  );
}
