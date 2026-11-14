import { Link } from 'react-router-dom';
import { MessageSquare } from 'lucide-react';
import type { Review } from '@/types/dto';
import { Avatar } from '@/components/common/Avatar';
import { Stars } from '@/components/common/Rating';
import { EmptyState } from '@/components/common/Feedback';
import { formatInstant } from '@/utils/format';

/** Reviews rendered as plain text (React escapes user content). */
export function ReviewList({ reviews, linkReviewers = true }: { reviews: Review[]; linkReviewers?: boolean }) {
  if (reviews.length === 0) {
    return <EmptyState icon={<MessageSquare className="size-8" aria-hidden />} title="No reviews yet" description="Reviews appear after completed rides." />;
  }
  return (
    <ul className="divide-y divide-line">
      {reviews.map((r) => (
        <li key={r.id} className="flex gap-4 py-5 first:pt-0">
          <Avatar name={r.reviewer.fullName} src={r.reviewer.profilePicture} size="sm" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              {linkReviewers ? (
                <Link to={`/users/${r.reviewer.id}`} className="font-medium hover:text-accent">
                  {r.reviewer.fullName}
                </Link>
              ) : (
                <span className="font-medium">{r.reviewer.fullName}</span>
              )}
              <Stars value={r.rating} />
              <span className="text-[13px] text-faint">{formatInstant(r.createdAt, true)}</span>
            </div>
            {r.comment && <p className="mt-1.5 whitespace-pre-line break-words leading-relaxed text-muted">{r.comment}</p>}
          </div>
        </li>
      ))}
    </ul>
  );
}
