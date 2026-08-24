import { useState, useEffect, useCallback } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { getAdminData } from '../api/sudokuApi.js';

const TABS = [
  { label: 'Games', entity: 'games' },
  { label: 'Players', entity: 'players' },
];

const GAME_COLUMNS = [
  { key: 'gameId', label: 'Game ID', isLink: true },
  { key: 'userId', label: 'User ID' },
  { key: 'difficulty', label: 'Difficulty' },
  { key: 'status', label: 'Status' },
  { key: 'timeSpentSeconds', label: 'Time (s)' },
  { key: 'hintsUsed', label: 'Hints' },
  { key: 'startedAt', label: 'Started At' },
  { key: 'endedAt', label: 'Ended At' },
];

const PLAYER_COLUMNS = [
  { key: 'userId', label: 'User ID', isLink: true },
  { key: 'email', label: 'Email' },
  { key: 'displayName', label: 'Display Name' },
  { key: 'createdAt', label: 'Created At' },
  { key: 'updatedAt', label: 'Updated At' },
];

function EntityTable({ columns, rows, onRowClick }) {
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            {columns.map((col) => (
              <TableCell key={col.key} sx={{ fontWeight: 700 }}>
                {col.label}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row[columns[0].key]} hover>
              {columns.map((col) => (
                <TableCell
                  key={col.key}
                  sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                >
                  {col.isLink ? (
                    <Link
                      component="button"
                      variant="body2"
                      onClick={() => onRowClick(row)}
                      sx={{ textAlign: 'left', fontFamily: 'monospace', fontSize: '0.75rem' }}
                    >
                      {row[col.key]}
                    </Link>
                  ) : (
                    <Typography
                      variant="body2"
                      component="span"
                      sx={{ fontFamily: col.key.endsWith('At') ? 'monospace' : 'inherit', fontSize: '0.8rem' }}
                    >
                      {String(row[col.key] ?? '')}
                    </Typography>
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))}
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={columns.length} align="center">
                <Typography variant="body2" color="text.secondary">
                  No records found
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function formatRecord(record) {
  // Pretty-print with 2-space indent, then collapse any array that contains
  // only integers onto a single line (matches Sudoku rows and candidate lists).
  return JSON.stringify(record, null, 2).replace(/\[[\s\d,]+\]/gs, (match) => match.replace(/\s+/g, ' ').trim());
}

function JsonDetailDialog({ open, record, onClose }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ fontFamily: 'monospace', fontSize: '1rem' }}>Record Detail</DialogTitle>
      <DialogContent dividers>
        <Box
          component="pre"
          sx={{
            m: 0,
            p: 1,
            fontSize: '0.75rem',
            overflowX: 'auto',
            bgcolor: 'background.default',
            borderRadius: 1,
            lineHeight: 1.5,
          }}
        >
          {record ? formatRecord(record) : ''}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

// @spec FE-UI-051 — Data Browser for inspecting DynamoDB game and player records, shown when
// VITE_DEV_TOOLS is true or the user is in the admin Cognito group (gating is done by the caller)
export default function DevDataDialog({ open, onClose }) {
  const [tabIndex, setTabIndex] = useState(0);
  const [dataByEntity, setDataByEntity] = useState({});
  const [loadingEntity, setLoadingEntity] = useState(null);
  const [errorByEntity, setErrorByEntity] = useState({});
  const [detailRecord, setDetailRecord] = useState(null);

  const currentEntity = TABS[tabIndex].entity;
  const currentColumns = currentEntity === 'games' ? GAME_COLUMNS : PLAYER_COLUMNS;
  const rows = dataByEntity[currentEntity] ?? [];
  const isLoading = loadingEntity === currentEntity;
  const error = errorByEntity[currentEntity];

  const loadEntity = useCallback(
    async (entity) => {
      if (dataByEntity[entity] !== undefined) return;
      setLoadingEntity(entity);
      try {
        const result = await getAdminData(entity);
        setDataByEntity((prev) => ({ ...prev, [entity]: result?.items ?? [] }));
      } catch (err) {
        setErrorByEntity((prev) => ({ ...prev, [entity]: err.message }));
      } finally {
        setLoadingEntity(null);
      }
    },
    [dataByEntity]
  );

  useEffect(() => {
    if (open) {
      loadEntity(currentEntity);
    }
  }, [open, currentEntity, loadEntity]);

  const handleTabChange = (_, newIndex) => {
    setTabIndex(newIndex);
  };

  const handleRefresh = () => {
    setDataByEntity((prev) => {
      const next = { ...prev };
      delete next[currentEntity];
      return next;
    });
    setErrorByEntity((prev) => {
      const next = { ...prev };
      delete next[currentEntity];
      return next;
    });
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="xl" fullWidth>
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography variant="h6">Data Browser</Typography>
            <Button size="small" onClick={handleRefresh} disabled={isLoading}>
              Refresh
            </Button>
          </Box>
        </DialogTitle>
        <DialogContent dividers sx={{ p: 0 }}>
          <Tabs value={tabIndex} onChange={handleTabChange} sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
            {TABS.map((tab) => (
              <Tab key={tab.entity} label={tab.label} />
            ))}
          </Tabs>
          <Box sx={{ p: 2 }}>
            {isLoading && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            )}
            {!isLoading && error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                Failed to load {currentEntity}: {error}
              </Alert>
            )}
            {!isLoading && !error && <EntityTable columns={currentColumns} rows={rows} onRowClick={setDetailRecord} />}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>

      <JsonDetailDialog open={!!detailRecord} record={detailRecord} onClose={() => setDetailRecord(null)} />
    </>
  );
}
