import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import 'leaflet/dist/leaflet.css';
import '@/styles/index.css';
import { queryClient } from '@/api/queryClient';
import { router } from '@/routes/router';
import { Toaster } from '@/components/common/Toaster';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { bootstrapSession, listenForCrossTabChanges } from '@/services/session';

void bootstrapSession();
listenForCrossTabChanges();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <Toaster />
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
