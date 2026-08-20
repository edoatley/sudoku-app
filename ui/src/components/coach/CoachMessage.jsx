// @spec SC-UI-002, SC-UI-020, SC-UI-021
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';

export default function CoachMessage({ role, content }) {
  const isUser = role === 'user';
  return (
    <Box sx={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', mb: 1 }}>
      <Paper
        elevation={0}
        sx={{
          px: 1.5,
          py: 1,
          maxWidth: '82%',
          bgcolor: isUser ? 'primary.main' : 'action.hover',
          color: isUser ? 'primary.contrastText' : 'text.primary',
          borderRadius: isUser ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
        }}
      >
        <Typography variant="body2">{content}</Typography>
      </Paper>
    </Box>
  );
}
