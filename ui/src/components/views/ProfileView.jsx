// @spec NAV-LAYOUT-001, NAV-LAYOUT-002, NAV-LAYOUT-003, UM-UI-001, UM-UI-002, UM-UI-003, UM-UI-004, UM-UI-005, UM-UI-006
import { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Tooltip from '@mui/material/Tooltip';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { AVATAR_ICONS } from '../../utils/avatarIcons.js';

const DEFAULT_AVATAR = 'Person';

export default function ProfileView({ navigateBack, playerProfile, currentAvatar, onProfileUpdate }) {
  const [displayName, setDisplayName] = useState(playerProfile?.displayName ?? '');
  const [selectedAvatar, setSelectedAvatar] = useState(currentAvatar ?? DEFAULT_AVATAR);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const trimmed = displayName.trim();
  const saveDisabled = saving || trimmed.length === 0;

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await onProfileUpdate({ displayName: trimmed, avatarKey: selectedAvatar });
      navigateBack();
    } catch (err) {
      setError(err?.message ?? 'Failed to save profile. Please try again.');
      setSaving(false);
    }
  };

  return (
    <>
      <AppBar position="sticky" elevation={1}>
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={navigateBack} aria-label="Back">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ ml: 1, flex: 1 }}>Profile</Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ maxWidth: 480, mx: 'auto', p: 3 }}>
        {playerProfile?.email && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {playerProfile.email}
          </Typography>
        )}

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

        <Grid container spacing={0.5} justifyContent="center" sx={{ mb: 2 }}>
          {AVATAR_ICONS.map(({ name, Component: AvatarIcon, label }) => {
            const selected = selectedAvatar === name;
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

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
          <Button onClick={navigateBack} disabled={saving}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saveDisabled}
            startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
          >
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </Box>
      </Box>
    </>
  );
}
