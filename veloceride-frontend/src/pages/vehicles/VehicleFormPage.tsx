import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { qk } from '@/api/queryClient';
import { vehicleService } from '@/services/vehicleService';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { ErrorState, Notice, PageLoader } from '@/components/common/Feedback';
import { Button, buttonClass } from '@/components/common/Button';
import { Input, Select } from '@/components/common/Field';
import { VEHICLE_TYPE_LABEL, VEHICLE_TYPES } from '@/constants/labels';
import type { Vehicle, VehicleRequest, VehicleType } from '@/types/dto';
import { vehicleSchema, type VehicleValues } from '@/utils/validation';
import { applyFieldErrors, errorStatus, getErrorMessage } from '@/utils/errors';

const FIELDS = ['vehicleName', 'vehicleNumber', 'vehicleType', 'brand', 'model', 'color', 'seatingCapacity', 'registrationDocumentUrl'] as const;

/** Pages 27/28. Only fields from VehicleRequest; ownership is enforced by the backend (403). */
export default function VehicleFormPage() {
  const { vehicleId } = useParams();
  const editing = Boolean(vehicleId);
  const query = useQuery({
    queryKey: qk.vehicle(vehicleId ?? ''),
    queryFn: () => vehicleService.get(vehicleId!),
    enabled: editing,
  });

  if (editing && query.isPending) return <PageLoader />;
  if (editing && query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  return <VehicleForm vehicle={query.data} />;
}

function VehicleForm({ vehicle }: { vehicle?: Vehicle }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<VehicleValues>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: vehicle
      ? {
          vehicleName: vehicle.vehicleName,
          vehicleNumber: vehicle.vehicleNumber,
          vehicleType: vehicle.vehicleType,
          brand: vehicle.brand,
          model: vehicle.model,
          color: vehicle.color,
          seatingCapacity: vehicle.seatingCapacity,
          registrationDocumentUrl: vehicle.registrationDocumentUrl ?? '',
        }
      : { vehicleName: '', vehicleNumber: '', vehicleType: '', brand: '', model: '', color: '', seatingCapacity: 4, registrationDocumentUrl: '' },
  });

  const onSubmit = handleSubmit(async (v) => {
    setFormError(null);
    const body: VehicleRequest = {
      vehicleName: v.vehicleName.trim(),
      vehicleNumber: v.vehicleNumber.trim(),
      vehicleType: v.vehicleType as VehicleType,
      brand: v.brand.trim(),
      model: v.model.trim(),
      color: v.color.trim(),
      seatingCapacity: v.seatingCapacity,
      registrationDocumentUrl: v.registrationDocumentUrl.trim() || undefined,
    };
    try {
      const saved = vehicle ? await vehicleService.update(vehicle.id, body) : await vehicleService.create(body);
      void queryClient.invalidateQueries({ queryKey: qk.vehicles });
      toast.success(
        vehicle ? 'Vehicle updated' : 'Vehicle added',
        saved.verificationStatus === 'PENDING' ? 'It will be usable once verified by our team.' : undefined,
      );
      navigate('/vehicles', { replace: true });
    } catch (err) {
      if (applyFieldErrors(err, setError, FIELDS)) return;
      if (errorStatus(err) === 409) return setError('vehicleNumber', { message: getErrorMessage(err) });
      setFormError(getErrorMessage(err));
    }
  });

  return (
    <div className="max-w-2xl">
      <PageHeader title={vehicle ? 'Edit vehicle' : 'Add a vehicle'} back={{ to: '/vehicles', label: 'Vehicles' }} />
      {vehicle && (
        <Notice className="mb-6">Changing the registration number or document sends the vehicle back for verification.</Notice>
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Input label="Nickname" placeholder="e.g. My Swift" maxLength={100} error={errors.vehicleName?.message} {...register('vehicleName')} />
        <div className="grid gap-5 sm:grid-cols-2">
          <Input label="Brand" placeholder="Maruti Suzuki" maxLength={50} error={errors.brand?.message} {...register('brand')} />
          <Input label="Model" placeholder="Swift" maxLength={50} error={errors.model?.message} {...register('model')} />
          <Input label="Colour" placeholder="White" maxLength={30} error={errors.color?.message} {...register('color')} />
          <Select label="Type" error={errors.vehicleType?.message} {...register('vehicleType')}>
            <option value="" disabled>
              Choose a type
            </option>
            {VEHICLE_TYPES.map((t) => (
              <option key={t} value={t}>
                {VEHICLE_TYPE_LABEL[t]}
              </option>
            ))}
          </Select>
          <Input
            label="Registration number"
            placeholder="DL 01 AB 1234"
            maxLength={20}
            autoCapitalize="characters"
            error={errors.vehicleNumber?.message}
            {...register('vehicleNumber')}
          />
          <Input
            type="number"
            label="Passenger seats"
            min={1}
            max={20}
            inputMode="numeric"
            hint="Excluding the driver"
            error={errors.seatingCapacity?.message}
            {...register('seatingCapacity', { setValueAs: (v: string) => (v === '' ? undefined : Number(v)) })}
          />
        </div>
        <Input
          type="url"
          label="Registration document link"
          optional
          placeholder="https://…"
          hint="A link to your RC document (upload isn’t supported yet)."
          error={errors.registrationDocumentUrl?.message}
          {...register('registrationDocumentUrl')}
        />
        <div className="flex flex-col-reverse gap-2 border-t border-line pt-6 sm:flex-row sm:justify-end">
          <Link to="/vehicles" className={buttonClass('ghost')}>
            Cancel
          </Link>
          <Button type="submit" size="lg" loading={isSubmitting}>
            {vehicle ? 'Save changes' : 'Add vehicle'}
          </Button>
        </div>
      </form>
    </div>
  );
}
