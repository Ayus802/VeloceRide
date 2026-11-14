import { Link } from 'react-router-dom';
import { buttonClass } from '@/components/common/Button';
import { Logo } from '@/components/layout/Logo';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center px-4 sm:px-6">
        <Logo />
      </div>
      <main className="flex flex-1 flex-col items-center justify-center gap-3 px-6 pb-24 text-center">
        <p className="text-sm font-medium text-accent">404</p>
        <h1 className="text-2xl font-semibold">This page took a wrong turn</h1>
        <p className="max-w-sm text-muted">The page you’re looking for doesn’t exist or has moved.</p>
        <Link to="/" className={buttonClass('primary', 'md', 'mt-3')}>
          Back home
        </Link>
      </main>
    </div>
  );
}
