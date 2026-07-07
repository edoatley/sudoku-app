import { useState, useRef } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';

export default function ImportModal({ open, isLoading, importStage, onConfirm, onCancel }) {
  const [imageFile, setImageFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const fileInputRef = useRef(null);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImageFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleConfirm = () => {
    if (imageFile) onConfirm(imageFile);
  };

  const handleCancel = () => {
    setImageFile(null);
    setPreviewUrl(null);
    onCancel();
  };

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="xs" fullWidth>
      <DialogTitle>Import Puzzle from Image</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, alignItems: 'center', pt: 1 }}>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
          <Button variant="outlined" onClick={() => fileInputRef.current?.click()} disabled={isLoading}>
            {imageFile ? 'Change Image' : 'Choose Image'}
          </Button>

          {previewUrl && (
            <Box
              component="img"
              src={previewUrl}
              alt="Selected puzzle"
              sx={{
                maxWidth: '100%',
                maxHeight: 200,
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'divider',
                objectFit: 'contain',
              }}
            />
          )}

          {!imageFile && !isLoading && (
            <Typography variant="body2" color="text.secondary" textAlign="center">
              Take a photo of a sudoku puzzle and import it to play.
            </Typography>
          )}

          {isLoading && (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, mt: 1 }}>
              <CircularProgress size={32} />
              <Typography variant="body2" color="text.secondary" textAlign="center">
                {importStage === 'analysing'
                  ? 'Analysing puzzle\u2026 this may take up to 60 seconds on first use.'
                  : 'Uploading image\u2026'}
              </Typography>
            </Box>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button variant="contained" onClick={handleConfirm} disabled={isLoading || !imageFile}>
          Import &amp; Play
        </Button>
      </DialogActions>
    </Dialog>
  );
}
