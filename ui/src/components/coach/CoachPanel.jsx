// @spec SC-UI-002, SC-UI-003, SC-UI-004, SC-RL-009
import { useState, useRef, useEffect } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import MonetizationOnIcon from '@mui/icons-material/MonetizationOn';
import Tooltip from '@mui/material/Tooltip';
import CoachMessage from './CoachMessage.jsx';

const QUICK_REPLIES = ["I'm stuck", 'Tell me more', 'Why does that work?'];

export default function CoachPanel({
  history,
  isLoading,
  onSend,
  onClose,
  tokensUsed = 0,
  monthlyTokenLimit = 100_000,
}) {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  const handleSend = () => {
    const text = input.trim();
    if (!text || isLoading) return;
    setInput('');
    onSend(text);
  };

  return (
    <Paper
      data-testid="coach-panel"
      elevation={6}
      sx={{
        position: 'fixed',
        bottom: 88,
        right: 24,
        width: 340,
        height: 480,
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 2,
        overflow: 'hidden',
        zIndex: 1200,
      }}
    >
      {/* Header */}
      <Box
        sx={{
          px: 2,
          py: 1,
          bgcolor: 'primary.main',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flexShrink: 0,
        }}
      >
        <Typography variant="subtitle2" sx={{ flexGrow: 1, color: 'white' }}>
          Sudoku Coach
        </Typography>
        <Tooltip title="AI tokens used this month">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3 }}>
            <MonetizationOnIcon sx={{ fontSize: 16, color: 'rgba(255,215,0,0.9)' }} />
            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.8)', whiteSpace: 'nowrap' }}>
              {tokensUsed.toLocaleString()} / {monthlyTokenLimit.toLocaleString()}
            </Typography>
          </Box>
        </Tooltip>
        <IconButton size="small" onClick={onClose} sx={{ color: 'white' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* Messages */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 1.5 }}>
        {history.map((msg, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: chat history is append-only, never reordered
          <CoachMessage key={i} role={msg.role} content={msg.content} />
        ))}
        {isLoading && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
            <CircularProgress size={14} />
            <Typography variant="caption" color="text.secondary">
              Thinking…
            </Typography>
          </Box>
        )}
        <div ref={messagesEndRef} />
      </Box>

      {/* Quick replies */}
      {!isLoading && (
        <Box sx={{ px: 1.5, pb: 0.5, display: 'flex', flexWrap: 'wrap', gap: 0.5, flexShrink: 0 }}>
          {QUICK_REPLIES.map((reply) => (
            <Chip key={reply} label={reply} size="small" variant="outlined" onClick={() => onSend(reply)} clickable />
          ))}
        </Box>
      )}

      {/* Input row */}
      <Box sx={{ px: 1.5, pb: 1.5, pt: 0.5, display: 'flex', gap: 1, flexShrink: 0 }}>
        <TextField
          size="small"
          fullWidth
          placeholder="Ask your coach…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          disabled={isLoading}
          autoComplete="off"
          inputProps={{ maxLength: 500 }}
        />
        <IconButton
          color="primary"
          onClick={handleSend}
          disabled={!input.trim() || isLoading}
          size="small"
          sx={{ flexShrink: 0 }}
        >
          <SendIcon />
        </IconButton>
      </Box>
    </Paper>
  );
}
