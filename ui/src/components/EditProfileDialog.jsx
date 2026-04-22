// @spec UM-UI-001, UM-UI-002, UM-UI-003, UM-UI-004, UM-UI-005, UM-UI-006
import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import { AVATAR_ICONS } from '../utils/avatarIcons.js';

const DEFAULT_AVATAR = 'Person';

export default function EditProfileDialog({ open, playerProfile, currentAvatar, onSave, onClose }) {
  const [displayName, setDisplayName] = useState('');
  const [selectedAvatar, setSelectedAvatar] = useState(DEFAULT_AVATAR);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  // Reset form state when dialog opens
  const handleEnter = () => {
    setDisplayName(playerProfile?.displayName ?? '');
    setSelectedAvatar(currentAvatar ?? DEFAULT_AVATAR);
    setError(null);
    setSaving(false);
  };

  const trimmed = displayName.trim();
  const saveDisabled = saving || trimmed.length === 0;

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await onSave({ displayName: trimmed, avatarKey: selectedAvatar });
      // onSave resolves after state is updated in the hook — just close
      void updated;
      onClose();
    } catch (err) {
      setError(err?.message ?? 'Failed to save profile. Please try again.');
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth TransitionProps={{ onEnter: handleEnter }}>
      <DialogTitle>Edit Profile</DialogTitle>
      <DialogContent>
        {/* Read-only email */}
        {playerProfile?.email && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {playerProfile.email}
          </Typography>
        )}

        {/* Display name field — UM-UI-006: Save disabled when blank */}
        <TextField
          label="Display name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          fullWidth
          size="small"
          inputProps={{ maxLength: 50 }}
          disabled={saving}
          sx={{ mb: 2 }}
        />

        <Divider sx={{ mb: 1.5 }} />

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          Avatar
        </Typography>

        {/* Inline avatar grid — UM-UI-001, UM-UI-002 */}
        <Grid container spacing={0.5} justifyContent="center">
          {AVATAR_ICONS.map(({ name, Component: AvatarIcon, label }) => {
            const selected = selectedAvatar === name;
            return (
              <Grid item key={name}>
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
                      onClick={() => setSelectedAvatar(name)}
                      color={selected ? 'primary' : 'default'}
                      size="medium"
                      aria-label={label}
                      disabled={saving}
                    >
                      <AvatarIcon />
                    </IconButton>
                  </Box>
                </Tooltip>
              </Grid>
            );
          })}
        </Grid>

        {/* Inline error — UM-UI-005 */}
        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        {/* UM-UI-003: loading indicator while in-flight; UM-UI-006: disabled when blank */}
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={saveDisabled}
          startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
        >
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
