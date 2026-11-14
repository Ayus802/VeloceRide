import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Mail, Smartphone } from 'lucide-react';
import { authService } from '@/services/authService';
import { pendingFlow, useAuthFlow } from '@/hooks/useAuthFlow';
import { useNow } from '@/hooks/useNow';
import { Button, buttonClass } from '@/components/common/Button';
import { EmptyState, Notice } from '@/components/common/Feedback';
import { OtpInput } from '@/components/forms/OtpInput';
import { toast } from '@/store/toastStore';
import type { ChannelStatus, VerificationChallenge, VerificationChannel } from '@/types/dto';
import { errorStatus, getErrorMessage } from '@/utils/errors';
import { cn } from '@/utils/cn';

const META: Record<VerificationChannel, { title: string; icon: typeof Mail; sentTo: string }> = {
  EMAIL: { title: 'Email address', icon: Mail, sentTo: 'Enter the 6-digit code we emailed you.' },
  PHONE: { title: 'Phone number', icon: Smartphone, sentTo: 'Enter the 6-digit code we sent to your phone (SMS or WhatsApp).' },
};

function ChannelStep({
  status,
  active,
  token,
  onChallenge,
  onExpired,
}: {
  status: ChannelStatus;
  active: boolean;
  token: string;
  onChallenge: (c: VerificationChallenge | null, done?: () => void) => void;
  onExpired: () => void;
}) {
  const handleFlow = useAuthFlow();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'confirm' | 'resend' | null>(null);
  // Cooldown counts down locally from the server-provided seconds.
  const [resendAt, setResendAt] = useState(() => Date.now() + status.resendAvailableInSeconds * 1000);
  const now = useNow(1000);
  const inputRef = useRef<HTMLInputElement>(null);
  const wait = Math.max(0, Math.ceil((resendAt - now) / 1000));
  const { title, icon: Icon, sentTo } = META[status.channel];

  useEffect(() => {
    if (active) inputRef.current?.focus();
  }, [active]);

  const confirm = async (value = code) => {
    if (value.length !== 6 || busy) return;
    setBusy('confirm');
    setError(null);
    try {
      const flow = await authService.confirmCode(token, status.channel, value);
      if (flow.status === 'AUTHENTICATED') {
        toast.success('You’re verified', 'Welcome to VeloceRide.');
        onChallenge(null, () => handleFlow(flow, pendingFlow.read()?.returnTo));
      } else {
        onChallenge(flow.verification ?? null);
      }
    } catch (err) {
      if (errorStatus(err) === 401) return onExpired();
      setError(getErrorMessage(err));
      setCode('');
      inputRef.current?.focus();
    } finally {
      setBusy(null);
    }
  };

  const resend = async () => {
    setBusy('resend');
    setError(null);
    try {
      const flow = await authService.resendCode(token, status.channel);
      const next = flow.verification?.channels.find((c) => c.channel === status.channel);
      setResendAt(Date.now() + (next?.resendAvailableInSeconds ?? 60) * 1000);
      setCode('');
      toast.success('New code sent', `Check your ${status.channel === 'EMAIL' ? 'inbox' : 'messages'}.`);
      inputRef.current?.focus();
    } catch (err) {
      if (errorStatus(err) === 401) return onExpired();
      setError(getErrorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  return (
    <li className={cn('rounded-xl border border-line bg-bg-2 p-5 shadow-card', !active && !status.verified && 'opacity-70')}>
      <div className="flex items-center gap-3">
        <span
          className={cn(
            'inline-flex size-9 items-center justify-center rounded-lg',
            status.verified ? 'bg-success/10 text-success' : 'bg-surface text-muted',
          )}
        >
          {status.verified ? <CheckCircle2 className="size-5" aria-hidden /> : <Icon className="size-5" aria-hidden />}
        </span>
        <div className="min-w-0">
          <p className="font-medium text-ink">{title}</p>
          <p className="truncate text-[13px] text-muted">
            {status.verified ? `${status.destination} · verified` : status.destination}
          </p>
        </div>
      </div>

      {!status.verified && (
        <form
          className="mt-5 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            void confirm();
          }}
        >
          <p className="text-[13px] text-muted">{sentTo}</p>
          <OtpInput
            ref={inputRef}
            label={`${title} code`}
            value={code}
            onChange={(v) => {
              setCode(v);
              setError(null);
            }}
            onComplete={(v) => void confirm(v)}
            error={error ?? undefined}
            disabled={busy === 'confirm'}
          />
          <div className="flex items-center justify-between gap-3">
            <button
              type="button"
              onClick={resend}
              disabled={wait > 0 || busy !== null}
              className="text-[13px] font-medium text-accent hover:underline disabled:cursor-not-allowed disabled:text-faint disabled:no-underline"
            >
              {wait > 0 ? `Resend code in ${wait}s` : busy === 'resend' ? 'Sending…' : 'Resend code'}
            </button>
            <Button type="submit" loading={busy === 'confirm'} disabled={code.length !== 6}>
              Verify
            </Button>
          </div>
        </form>
      )}
    </li>
  );
}

/** Step 2 of sign-up (and of sign-in for unverified accounts): confirm the email and SMS codes. */
export default function VerifyPage() {
  const [challenge, setChallenge] = useState<VerificationChallenge | null>(() => pendingFlow.read()?.verification ?? null);
  const [expired, setExpired] = useState(false);

  if (expired || !challenge) {
    return (
      <EmptyState
        title={expired ? 'This verification session has expired' : 'Nothing to verify'}
        description="Sign in again and we’ll send you fresh codes."
        action={
          <Link to="/login" className={buttonClass('primary')}>
            Go to log in
          </Link>
        }
      />
    );
  }

  const pending = challenge.channels.filter((c) => !c.verified);
  const activeChannel = pending[0]?.channel;

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Verify your account</h1>
      <p className="mt-1 text-muted">
        {pending.length === 2
          ? 'Confirm your email address and phone number to finish creating your account.'
          : 'One more step: confirm the code we sent you.'}
      </p>
      <Notice className="mt-6">Codes expire after 10 minutes. Didn’t get one? Check spam, or resend after a minute.</Notice>
      <ol className="mt-6 space-y-4">
        {challenge.channels.map((status) => (
          <ChannelStep
            key={`${status.channel}-${status.verified}`}
            status={status}
            active={status.channel === activeChannel}
            token={challenge.verificationToken}
            onChallenge={(next, done) => {
              if (next) {
                setChallenge(next);
                pendingFlow.write({ ...pendingFlow.read(), verification: next });
              }
              done?.();
            }}
            onExpired={() => {
              pendingFlow.clear();
              setExpired(true);
            }}
          />
        ))}
      </ol>
      <p className="mt-8 text-center text-[13px] text-muted">
        Wrong details?{' '}
        <Link to="/register" onClick={() => pendingFlow.clear()} className="font-medium text-accent hover:underline">
          Start over
        </Link>
      </p>
    </>
  );
}
