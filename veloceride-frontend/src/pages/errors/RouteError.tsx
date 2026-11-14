import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { buttonClass } from '@/components/common/Button';

/** Fallback for render errors and failed lazy chunk loads. */
export function RouteError() {
  const error = useRouteError();
  const chunkFailed = error instanceof Error && /dynamically imported module|Loading chunk/i.test(error.message);
  const title = isRouteErrorResponse(error) && error.status === 404 ? 'Page not found' : 'Something went wrong';
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center">
      <h1 className="text-xl font-semibold">{chunkFailed ? 'A new version is available' : title}</h1>
      <p className="max-w-sm text-muted">
        {chunkFailed ? 'Reload the page to continue.' : 'An unexpected error occurred. You can reload or go back home.'}
      </p>
      <div className="mt-2 flex gap-2">
        <button type="button" className={buttonClass('primary')} onClick={() => window.location.reload()}>
          Reload
        </button>
        <Link to="/" className={buttonClass('secondary')}>
          Home
        </Link>
      </div>
    </div>
  );
}
