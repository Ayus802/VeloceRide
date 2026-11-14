import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search, UserCheck, UserX } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { useDebouncedValue } from '@/hooks/useNow';
import { PageHeader } from '@/components/layout/PageHeader';
import { DataTable } from '@/components/common/DataTable';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination } from '@/components/common/Navigation';
import { Badge } from '@/components/common/StatusBadge';
import { Avatar } from '@/components/common/Avatar';
import { ActionsMenu, DropdownItem } from '@/components/dropdowns/Dropdown';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import type { AdminUser } from '@/types/dto';
import { formatInstant } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

export default function AdminUsersPage() {
  const me = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const debounced = useDebouncedValue(q.trim());
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [target, setTarget] = useState<AdminUser | null>(null);

  const query = useQuery({
    queryKey: [...qk.admin, 'users', { q: debounced, page }],
    queryFn: () => adminService.users({ q: debounced || undefined, page, size: 20, sort: 'createdAt,desc' }),
    placeholderData: keepPreviousData,
  });

  const setStatus = useMutation({
    mutationFn: (u: AdminUser) => adminService.setUserStatus(u.id, u.accountStatus === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE'),
    onSuccess: (u) => {
      toast.success(u.accountStatus === 'ACTIVE' ? `${u.fullName} reactivated` : `${u.fullName} deactivated`);
      void queryClient.invalidateQueries({ queryKey: qk.admin });
      setTarget(null);
    },
    onError: (err) => {
      toast.error('Couldn’t update the account', getErrorMessage(err));
      setTarget(null);
    },
  });

  return (
    <>
      <PageHeader title="Users" />
      <label className="relative mb-4 block max-w-sm">
        <span className="sr-only">Search users</span>
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint" aria-hidden />
        <input
          type="search"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setParams(e.target.value ? { q: e.target.value } : {}, { replace: true });
          }}
          placeholder="Name, email or phone"
          className="h-10 w-full rounded-lg border border-line bg-bg-2 pl-9 pr-3 text-sm placeholder:text-faint focus:border-accent focus:outline-none"
        />
      </label>
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState title="No users match" />
      ) : (
        <>
          <DataTable<AdminUser>
            caption="Users"
            rows={query.data.content}
            rowKey={(u) => u.id}
            columns={[
              {
                header: 'User',
                cell: (u) => (
                  <div className="flex items-center gap-3">
                    <Avatar name={u.fullName} src={u.profilePicture} size="sm" />
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {u.fullName} {u.role === 'ADMIN' && <Badge tone="accent">Admin</Badge>}
                      </p>
                      <p className="truncate text-[13px] text-muted">{u.email}</p>
                    </div>
                  </div>
                ),
              },
              { header: 'Phone', cell: (u) => <span className="text-muted">{u.phoneNumber}</span>, hideOnMobile: true },
              {
                header: 'Status',
                cell: (u) => <Badge tone={u.accountStatus === 'ACTIVE' ? 'success' : 'danger'}>{u.accountStatus === 'ACTIVE' ? 'Active' : 'Deactivated'}</Badge>,
              },
              { header: 'Joined', cell: (u) => <span className="text-muted">{formatInstant(u.createdAt, true)}</span>, hideOnMobile: true },
              {
                header: '',
                className: 'w-12 text-right',
                cell: (u) =>
                  u.id !== me?.id && (
                    <ActionsMenu label={`Actions for ${u.fullName}`}>
                      {u.accountStatus === 'ACTIVE' ? (
                        <DropdownItem tone="danger" icon={<UserX />} onSelect={() => setTarget(u)}>
                          Deactivate
                        </DropdownItem>
                      ) : (
                        <DropdownItem icon={<UserCheck />} onSelect={() => setTarget(u)}>
                          Reactivate
                        </DropdownItem>
                      )}
                    </ActionsMenu>
                  ),
              },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ ...(q ? { q } : {}), page: String(p) })} />
        </>
      )}
      <ConfirmDialog
        open={Boolean(target)}
        title={target?.accountStatus === 'ACTIVE' ? `Deactivate ${target?.fullName}?` : `Reactivate ${target?.fullName}?`}
        description={target?.accountStatus === 'ACTIVE' ? 'They’ll be signed out everywhere and won’t be able to log in.' : 'They’ll be able to log in again.'}
        confirmLabel={target?.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
        cancelLabel="Cancel"
        tone={target?.accountStatus === 'ACTIVE' ? 'danger' : 'primary'}
        loading={setStatus.isPending}
        onConfirm={() => target && setStatus.mutate(target)}
        onClose={() => setTarget(null)}
      />
    </>
  );
}
