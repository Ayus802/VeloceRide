import { useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck, Trash2 } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { notificationService } from '@/services/notificationService';
import { notificationTarget } from '@/utils/notificationTarget';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/common/Button';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination, Tabs } from '@/components/common/Navigation';
import { ActionsMenu, DropdownItem } from '@/components/dropdowns/Dropdown';
import type { AppNotification } from '@/types/dto';
import { timeAgo } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';
import { cn } from '@/utils/cn';

export default function NotificationsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const filter = params.get('filter') === 'unread' ? 'unread' : 'all';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const read = filter === 'unread' ? false : undefined;

  const query = useQuery({
    queryKey: qk.notificationList({ read, page }),
    queryFn: () => notificationService.list({ read, page, size: 20 }),
    placeholderData: keepPreviousData,
  });
  const unread = useQuery({ queryKey: qk.unreadCount, queryFn: notificationService.unreadCount });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: qk.notifications });
  const markRead = useMutation({ mutationFn: notificationService.markRead, onSuccess: invalidate });
  const markAll = useMutation({
    mutationFn: notificationService.markAllRead,
    onSuccess: () => {
      void invalidate();
      toast.success('All caught up');
    },
    onError: (err) => toast.error('Couldn’t mark as read', getErrorMessage(err)),
  });
  const remove = useMutation({
    mutationFn: notificationService.remove,
    onSuccess: invalidate,
    onError: (err) => toast.error('Couldn’t delete', getErrorMessage(err)),
  });

  const open = async (n: AppNotification) => {
    if (!n.isRead) markRead.mutate(n.id);
    try {
      const target = await notificationTarget(n);
      if (target) navigate(target);
    } catch (err) {
      toast.error('Couldn’t open this item', getErrorMessage(err));
    }
  };

  const unreadCount = unread.data?.unreadCount ?? 0;

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Notifications"
        actions={
          unreadCount > 0 && (
            <Button size="sm" variant="secondary" icon={<CheckCheck className="size-4" />} loading={markAll.isPending} onClick={() => markAll.mutate()}>
              Mark all as read
            </Button>
          )
        }
      />
      <Tabs
        className="mb-4"
        value={filter}
        onChange={(f) => setParams(f === 'all' ? {} : { filter: f })}
        items={[
          { value: 'all', label: 'All' },
          { value: 'unread', label: 'Unread', count: unreadCount },
        ]}
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={<Bell className="size-8" aria-hidden />} title={filter === 'unread' ? 'No unread notifications' : 'No notifications yet'} />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {query.data.content.map((n) => (
              <li key={n.id} className="flex items-start gap-3 py-1">
                <button
                  type="button"
                  onClick={() => open(n)}
                  className="flex min-w-0 flex-1 items-start gap-3 rounded-lg px-2 py-3 text-left transition-colors hover:bg-surface/50"
                >
                  <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.isRead ? 'bg-transparent' : 'bg-accent')} aria-hidden />
                  <span className="min-w-0 flex-1">
                    <span className={cn('block', n.isRead ? 'text-muted' : 'font-medium text-ink')}>
                      {n.title}
                      {!n.isRead && <span className="sr-only"> (unread)</span>}
                    </span>
                    <span className="mt-0.5 block break-words text-[13px] text-muted">{n.message}</span>
                    <span className="mt-1 block text-xs text-faint">{timeAgo(n.createdAt)}</span>
                  </span>
                </button>
                <div className="pt-2">
                  <ActionsMenu label="Notification actions">
                    {!n.isRead && (
                      <DropdownItem icon={<CheckCheck />} onSelect={() => markRead.mutate(n.id)}>
                        Mark as read
                      </DropdownItem>
                    )}
                    <DropdownItem tone="danger" icon={<Trash2 />} onSelect={() => remove.mutate(n.id)}>
                      Delete
                    </DropdownItem>
                  </ActionsMenu>
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ ...(filter === 'unread' ? { filter } : {}), page: String(p) })} />
        </>
      )}
    </div>
  );
}
