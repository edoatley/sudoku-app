// @spec FE-UI-015
// Tutorial markdown files are static assets committed to ui/public/techniques/{slug}.md
// (one file per strategy slug). They are served directly by Vite's dev server and
// copied into the Amplify build output at build time — no runtime generation needed.
import { useState, useEffect } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CloseIcon from '@mui/icons-material/Close';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

function slugToTitle(slug) {
  if (!slug) return '';
  return slug
    .split('-')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

function stripFrontmatter(text) {
  return text.replace(/^---[\s\S]*?---\n?/, '');
}

export default function TutorialModal({ open, slug, src, title, onClose }) {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return;
    const url = src ?? `/techniques/${slug}.md`;
    if (!url) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load tutorial (${res.status})`);
        return res.text();
      })
      .then((text) => {
        if (!cancelled) {
          setContent(stripFrontmatter(text));
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message);
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [slug, src, open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2, pr: 6 }}>
        {title ?? slugToTitle(slug)}
        <IconButton onClick={onClose} size="small" title="Close" sx={{ position: 'absolute', right: 8, top: 8 }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {error && <Alert severity="error">{error}</Alert>}
        {!loading && !error && <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>}
      </DialogContent>
    </Dialog>
  );
}
