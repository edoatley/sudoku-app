# Frontend Development Guidelines

**Stack:** React, Vite, Material UI (MUI)

## Architecture & Styling

- **Strict MUI Usage:** Use `@mui/material` components for all UI elements. Do not write custom CSS or use Tailwind/Bootstrap. Use the `sx` prop for minor layout adjustments.
- **Component Structure:** Keep components small and focused. Separate the Sudoku grid visual rendering (`<SudokuGrid />`, `<SudokuCell />`) from the game logic.
- **State Management:** Rely purely on React Hooks (`useState`, `useEffect`, `useCallback`). Do not introduce Redux, Zustand, or other global state libraries.
- **Responsiveness:** Ensure the layout works seamlessly on mobile and desktop using MUI's `<Grid>` or `<Stack>`. The Sudoku board must scale appropriately without breaking the viewport.

## API & Data

- **Data Fetching:** Use the native `fetch` API. Do not install Axios.
- **Environment Variables:** Always read the backend API URL from Vite's environment config: `import.meta.env.VITE_API_URL`.
- **Error Handling:** Gracefully handle API failures (e.g., cold start timeouts or network errors) and display user-friendly error messages using MUI's `<Snackbar>` or `<Alert>`.