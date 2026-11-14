import { lazy, Suspense, type ComponentType } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AdminLayout, AppLayout, AuthLayout, PublicLayout } from '@/layouts/Layouts';
import { AdminRoute, GuestRoute, ProtectedRoute } from '@/routes/guards';
import { PageLoader } from '@/components/common/Feedback';
import { RouteError } from '@/pages/errors/RouteError';

/** Lazy page with a shared fallback; keeps Leaflet/admin code out of the initial bundle. */
function page(loader: () => Promise<{ default: ComponentType }>) {
  const Component = lazy(loader);
  return (
    <Suspense fallback={<PageLoader />}>
      <Component />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      {
        element: <PublicLayout />,
        children: [
          { path: '/', element: page(() => import('@/pages/public/HomePage')) },
          { path: '/rides/search', element: page(() => import('@/pages/passenger/SearchRidesPage')) },
          { path: '/rides/:rideId', element: page(() => import('@/pages/passenger/RideDetailsPage')) },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          {
            element: <GuestRoute />,
            children: [
              { path: '/login', element: page(() => import('@/pages/auth/LoginPage')) },
              { path: '/register', element: page(() => import('@/pages/auth/RegisterPage')) },
              { path: '/forgot-password', element: page(() => import('@/pages/auth/ForgotPasswordPage')) },
            ],
          },
          // Reachable signed in or out (link comes by email).
          { path: '/reset-password', element: page(() => import('@/pages/auth/ResetPasswordPage')) },
          // Multi-step sign-up/sign-in; they finish by starting a session, so not behind GuestRoute.
          { path: '/verify', element: page(() => import('@/pages/auth/VerifyPage')) },
          { path: '/complete-signup', element: page(() => import('@/pages/auth/CompleteSignupPage')) },
        ],
      },
      {
        element: (
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        ),
        children: [
          { path: '/dashboard', element: page(() => import('@/pages/dashboard/DashboardPage')) },
          { path: '/profile', element: page(() => import('@/pages/profile/ProfilePage')) },
          { path: '/profile/edit', element: page(() => import('@/pages/profile/EditProfilePage')) },
          { path: '/reviews', element: page(() => import('@/pages/profile/MyReviewsPage')) },
          { path: '/users/:userId', element: page(() => import('@/pages/profile/PublicProfilePage')) },
          { path: '/settings', element: page(() => import('@/pages/profile/SettingsPage')) },
          { path: '/notifications', element: page(() => import('@/pages/notifications/NotificationsPage')) },

          { path: '/bookings', element: page(() => import('@/pages/passenger/MyBookingsPage')) },
          { path: '/bookings/:bookingId', element: page(() => import('@/pages/passenger/BookingDetailsPage')) },
          { path: '/rides/:rideId/track', element: page(() => import('@/pages/passenger/TrackRidePage')) },

          { path: '/rides/new', element: page(() => import('@/pages/rider/PublishRidePage')) },
          { path: '/my-rides', element: page(() => import('@/pages/rider/MyRidesPage')) },
          { path: '/my-rides/:rideId', element: page(() => import('@/pages/rider/ManageRidePage')) },
          { path: '/my-rides/:rideId/edit', element: page(() => import('@/pages/rider/EditRidePage')) },
          { path: '/my-rides/:rideId/live', element: page(() => import('@/pages/rider/DriverConsolePage')) },
          { path: '/requests', element: page(() => import('@/pages/rider/BookingRequestsPage')) },

          { path: '/vehicles', element: page(() => import('@/pages/vehicles/MyVehiclesPage')) },
          { path: '/vehicles/new', element: page(() => import('@/pages/vehicles/VehicleFormPage')) },
          { path: '/vehicles/:vehicleId/edit', element: page(() => import('@/pages/vehicles/VehicleFormPage')) },
        ],
      },
      {
        path: '/admin',
        element: (
          <ProtectedRoute>
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [
          {
            element: <AdminRoute />,
            children: [
              { index: true, element: page(() => import('@/pages/admin/AdminDashboardPage')) },
              { path: 'users', element: page(() => import('@/pages/admin/AdminUsersPage')) },
              { path: 'vehicles', element: page(() => import('@/pages/admin/AdminVehiclesPage')) },
              { path: 'rides', element: page(() => import('@/pages/admin/AdminRidesPage')) },
              { path: 'bookings', element: page(() => import('@/pages/admin/AdminBookingsPage')) },
              { path: 'reviews', element: page(() => import('@/pages/admin/AdminReviewsPage')) },
            ],
          },
        ],
      },
      { path: '/home', element: <Navigate to="/" replace /> },
      { path: '*', element: page(() => import('@/pages/errors/NotFoundPage')) },
    ],
  },
]);
