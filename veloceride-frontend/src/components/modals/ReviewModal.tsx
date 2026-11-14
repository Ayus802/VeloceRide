import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Modal } from '@/components/modals/Modal';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { RatingInput } from '@/components/common/Rating';
import { reviewService } from '@/services/reviewService';
import { qk } from '@/api/queryClient';
import { toast } from '@/store/toastStore';
import { reviewSchema, type ReviewValues } from '@/utils/validation';
import { getErrorMessage } from '@/utils/errors';

/**
 * Rate a co-traveller after a COMPLETED ride. Eligibility (participant, passenger→driver only,
 * no duplicates) is enforced by the backend; its 403/409/422 messages are shown as-is.
 */
export function ReviewModal({
  open,
  onClose,
  rideId,
  reviewee,
  role,
}: {
  open: boolean;
  onClose: () => void;
  rideId: string;
  reviewee: { id: string; fullName: string };
  role: 'driver' | 'passenger';
}) {
  const queryClient = useQueryClient();
  const { control, register, handleSubmit, reset, formState } = useForm<ReviewValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: { rating: 0, comment: '' },
  });

  const mutation = useMutation({
    mutationFn: (v: ReviewValues) =>
      reviewService.create({ rideId, reviewedUserId: reviewee.id, rating: v.rating, comment: v.comment.trim() || undefined }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: qk.rideReviews(rideId) });
      toast.success('Review submitted', `Thanks for rating ${reviewee.fullName}.`);
      reset();
      onClose();
    },
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={mutation.isPending}
      title={`Rate ${reviewee.fullName}`}
      description={role === 'driver' ? 'How was the ride with your driver?' : 'How was this passenger?'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            Not now
          </Button>
          <Button onClick={handleSubmit((v) => mutation.mutate(v))} loading={mutation.isPending}>
            Submit review
          </Button>
        </>
      }
    >
      <form className="space-y-5" onSubmit={handleSubmit((v) => mutation.mutate(v))} noValidate>
        <Controller
          control={control}
          name="rating"
          render={({ field }) => <RatingInput value={field.value} onChange={field.onChange} error={formState.errors.rating?.message} />}
        />
        <Textarea
          label="Comment"
          optional
          maxLength={1000}
          placeholder="Punctual, friendly, smooth drive…"
          error={formState.errors.comment?.message}
          {...register('comment')}
        />
        {mutation.isError && <Notice tone="danger">{getErrorMessage(mutation.error)}</Notice>}
      </form>
    </Modal>
  );
}
