import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Modal } from '@/components/modals/Modal';
import { Button } from '@/components/common/Button';
import { Notice } from '@/components/common/Feedback';
import { OtpInput } from '@/components/forms/OtpInput';
import { userService } from '@/services/userService';
import { useAuthStore } from '@/store/authStore';
import { useNow } from '@/hooks/useNow';
import { qk } from '@/api/queryClient';
import { toast } from '@/store/toastStore';
import type { VerificationChannel } from '@/types/dto';
import { errorStatus, getErrorMessage } from '@/utils/errors';

/** Signed-in re-verification (e.g. after changing the phone number). Sends a code on open. */
export function VerifyContactModal({ channel, open, onClose }: { channel: VerificationChannel; open: boolean; onClose: () => void }) {
  const setUser = useAuthStore((s) => s.setUser);
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [destination, setDestination] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'send' | 'confirm' | null>(null);
  const [resendAt, setResendAt] = useState(0);
  const now = useNow(1000);
  const sentOnce = useRef(false);
  const wait = Math.max(0, Math.ceil((resendAt - now) / 1000));
  const label = channel === 'PHONE' ? 'phone number' : 'email address';

  const send = async () => {
    setBusy('send');
    setError(null);
    try {
      const status = await userService.sendVerification(channel);
      setDestination(status.destination);
      setResendAt(Date.now() + status.resendAvailableInSeconds * 1000);
    } catch (err) {
      // 429 = a code was sent moments ago; it can still be used.
      setError(errorStatus(err) === 429 ? `${getErrorMessage(err)}. Use the code you already received.` : getErrorMessage(err));
      if (errorStatus(err) === 429) setResendAt(Date.now() + 30_000);
    } finally {
      setBusy(null);
    }
  };

  useEffect(() => {
    if (open && !sentOnce.current) {
      sentOnce.current = true;
      void send();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const confirm = async (value = code) => {
    if (value.length !== 6) return;
    setBusy('confirm');
    setError(null);
    try {
      const user = await userService.confirmVerification(channel, value);
      setUser(user);
      queryClient.setQueryData(qk.me, user);
      toast.success(channel === 'PHONE' ? 'Phone number verified' : 'Email verified');
      onClose();
    } catch (err) {
      setError(getErrorMessage(err));
      setCode('');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={busy === 'confirm'}
      title={`Verify your ${label}`}
      description={destination ? `Enter the 6-digit code we sent to ${destination}.` : `We’re sending a code to your ${label}…`}
      footer={
        <>
          <Button variant="ghost" onClick={send} disabled={wait > 0 || busy !== null}>
            {wait > 0 ? `Resend in ${wait}s` : 'Resend code'}
          </Button>
          <Button onClick={() => void confirm()} loading={busy === 'confirm'} disabled={code.length !== 6}>
            Verify
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <OtpInput label="Verification code" value={code} onChange={setCode} onComplete={(v) => void confirm(v)} autoFocus />
        {error && <Notice tone="danger">{error}</Notice>}
      </div>
    </Modal>
  );
}
