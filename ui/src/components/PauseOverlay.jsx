import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';

export default function PauseOverlay({ onResume }) {
  return (
    <Box
      data-testid="pause-overlay"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        width: { xs: '100%', md: 450 },
        minHeight: 450,
        bgcolor: 'grey.100',
        borderRadius: 2,
        border: '2px solid',
        borderColor: 'grey.300',
        gap: 2,
        py: 6,
      }}
    >
      <LockOutlinedIcon sx={{ fontSize: 56, color: 'text.secondary' }} />
      <Typography variant="h5" fontWeight="bold" color="text.secondary">
        Game Paused
      </Typography>
      <Button variant="contained" size="large" onClick={onResume} sx={{ mt: 1 }}>
        Resume
      </Button>
    </Box>
  );
}
