import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { Dropdown, DropdownItem } from '@/components/dropdowns/Dropdown';
import { Segmented } from '@/components/common/Navigation';
import { useTheme, type ThemePreference } from '@/theme/useTheme';

const OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', Icon: Sun },
  { value: 'dark', label: 'Dark', Icon: Moon },
  { value: 'system', label: 'System', Icon: Monitor },
];

/** Compact icon button + menu, for headers without a profile menu (landing, auth, public pages). */
export function ThemeMenu() {
  const { preference, resolved, setPreference } = useTheme();
  const TriggerIcon = preference === 'system' ? Monitor : resolved === 'dark' ? Moon : Sun;
  return (
    <Dropdown
      menuClassName="w-40"
      trigger={(props) => (
        <button
          type="button"
          aria-label={`Theme: ${OPTIONS.find((o) => o.value === preference)!.label}`}
          title="Theme"
          className="inline-flex size-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface hover:text-ink"
          {...props}
        >
          <TriggerIcon className="size-[18px]" aria-hidden />
        </button>
      )}
    >
      {OPTIONS.map(({ value, label, Icon }) => (
        <DropdownItem key={value} icon={<Icon />} onSelect={() => setPreference(value)}>
          <span className="flex-1">{label}</span>
          {preference === value && <Check className="size-4 text-accent" aria-label="selected" />}
        </DropdownItem>
      ))}
    </Dropdown>
  );
}

/** Segmented Light / Dark / System selector (profile menu and Settings). */
export function ThemeSegmented({ showLabels = true }: { showLabels?: boolean }) {
  const { preference, setPreference } = useTheme();
  return (
    <Segmented
      label="Appearance"
      value={preference}
      onChange={setPreference}
      options={OPTIONS.map(({ value, label, Icon }) => ({
        value,
        label: (
          <span className="inline-flex items-center gap-1.5" title={label}>
            <Icon className="size-3.5" aria-hidden />
            {showLabels ? label : <span className="sr-only">{label}</span>}
          </span>
        ),
      }))}
    />
  );
}
