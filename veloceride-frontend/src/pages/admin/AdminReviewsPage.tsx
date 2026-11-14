import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, EyeOff } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination, Tabs } from '@/components/common/Navigation';
import { Badge } from '@/components/common/StatusBadge';
import { Stars } from '@/components/common/Rating';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Field';
import { Modal } from '@/components/modals/Modal';
import type { AdminReview } from '@/types/dto';
import { formatInstant } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

type Filter = 'all' | 'visible' | 'hidden';

/** Page 33: hide/restore reviews. Hidden reviews are excluded from ratings by the backend. */
export default function AdminReviewsPage() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const filter = (['visible', 'hidden'].includes(params.get('filter') ?? '') ? params.get('filter') : 'all') as Filter;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const hidden = filter === 'all' ? undefined : filter === 'hidden';
  const [target, setTarget] = useState<AdminReview | null>(null);
  const [note, setNote] = useState('');

  const query = useQuery({
    queryKey: [...qk.admin, 'reviews', { hidden, page }],
    queryFn: () => adminService.reviews({ hidden, page, size: 20 }),
    placeholderData: keepPreviousData,
  });
  const moderate = useMutation({
    mutationFn: () => adminService.moderateReview(target!.id, !target!.hidden, note.trim()),
    onSuccess: (r) => {
      toast.success(r.hidden ? 'Review hidden' : 'Review restored');
      void queryClient.invalidateQueries({ queryKey: qk.admin });
      setTarget(null);
      setNote('');
    },
  });

  return (
    <>
      <PageHeader title="Reviews" description="Hidden reviews don’t appear on profiles and don’t count towards ratings." />
      <Tabs
        className="mb-4"
        value={filter}
        onChange={(f) => setParams(f === 'all' ? {} : { filter: f })}
        items={[
          { value: 'all', label: 'All' },
          { value: 'visible', label: 'Visible' },
          { value: 'hidden', label: 'Hidden' },
        ]}
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState title="No reviews" />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {query.data.content.map((r) => (
              <li key={r.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-start">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px]">
                    <span className="font-medium text-ink">{r.reviewer.fullName}</span>
                    <span className="text-faint">→</span>
                    <span className="font-medium text-ink">{r.reviewedUser.fullName}</span>
                    <Stars value={r.rating} />
                    <span className="text-faint">{formatInstant(r.createdAt, true)}</span>
                    {r.hidden && <Badge tone="danger">Hidden</Badge>}
                  </div>
                  {r.comment ? (
                    <p className="mt-1.5 whitespace-pre-line break-words text-muted">{r.comment}</p>
                  ) : (
                    <p className="mt-1.5 text-faint">No comment</p>
                  )}
                  {r.moderationNote && <p className="mt-1 text-[13px] text-faint">Moderation note: {r.moderationNote}</p>}
                </div>
                <Button
                  size="sm"
                  variant={r.hidden ? 'secondary' : 'danger'}
                  icon={r.hidden ? <Eye className="size-4" /> : <EyeOff className="size-4" />}
                  onClick={() => {
                    setNote(r.moderationNote ?? '');
                    setTarget(r);
                  }}
                >
                  {r.hidden ? 'Restore' : 'Hide'}
                </Button>
              </li>
            ))}
          </ul>
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ ...(filter !== 'all' ? { filter } : {}), page: String(p) })} />
        </>
      )}

      <Modal
        open={Boolean(target)}
        onClose={() => setTarget(null)}
        busy={moderate.isPending}
        title={target?.hidden ? 'Restore this review?' : 'Hide this review?'}
        description={target?.hidden ? 'It will count towards the user’s rating again.' : 'It will be removed from the profile and rating.'}
        footer={
          <>
            <Button variant="ghost" onClick={() => setTarget(null)} disabled={moderate.isPending}>
              Cancel
            </Button>
            <Button variant={target?.hidden ? 'primary' : 'danger'} loading={moderate.isPending} onClick={() => moderate.mutate()}>
              {target?.hidden ? 'Restore' : 'Hide review'}
            </Button>
          </>
        }
      >
        <Textarea
          label="Moderation note"
          optional
          maxLength={500}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="e.g. Abusive language"
          error={moderate.isError ? getErrorMessage(moderate.error) : undefined}
        />
      </Modal>
    </>
  );
}
