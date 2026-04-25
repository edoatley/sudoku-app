import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';
import { AVATAR_ICONS } from '../utils/avatarIcons.js';

export default function AvatarPickerDialog({ open, currentAvatar, onSelect, onClose }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Choose Avatar</DialogTitle>
      <DialogContent>
        <Grid container spacing={1} justifyContent="center">
          {AVATAR_ICONS.map((iconEntry) => {
            const { name, Component: AvatarIcon, label } = iconEntry;
            const selected = currentAvatar === name;
            return (
              <Grid key={name}>
                <Tooltip title={label} placement="top">
                  <Box
                    sx={{
                      borderRadius: '50%',
                      outline: selected ? '2px solid' : '2px solid transparent',
                      outlineColor: selected ? 'primary.main' : 'transparent',
                      outlineOffset: '2px',
                    }}
                  >
                    <IconButton
                      onClick={() => onSelect(name)}
                      color={selected ? 'primary' : 'default'}
                      size="large"
                      aria-label={label}
                    >
                      <AvatarIcon fontSize="large" />
                    </IconButton>
                  </Box>
                </Tooltip>
              </Grid>
            );
          })}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
