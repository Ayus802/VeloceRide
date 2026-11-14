import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Link } from 'react-router-dom';
import { MoreHorizontal } from 'lucide-react';
import { cn } from '@/utils/cn';

const CloseCtx = createContext<() => void>(() => {});

interface DropdownProps {
  /** Render the trigger; spread `props` onto a button. */
  trigger: (props: {
    'aria-haspopup': 'menu';
    'aria-expanded': boolean;
    'aria-controls': string;
    onClick: () => void;
    onKeyDown: (e: React.KeyboardEvent) => void;
  }) => ReactNode;
  children: ReactNode;
  align?: 'start' | 'end';
  className?: string;
  menuClassName?: string;
}

/** Accessible menu: click/Enter opens, Escape or outside click closes, arrow keys move focus. */
export function Dropdown({ trigger, children, align = 'end', className, menuClassName }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const close = useCallback(() => setOpen(false), []);

  const focusItem = (index: number) => {
    const items = menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])');
    if (!items?.length) return;
    items[(index + items.length) % items.length].focus();
  };

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onPointer);
    requestAnimationFrame(() => focusItem(0));
    return () => document.removeEventListener('mousedown', onPointer);
  }, [open]);

  const onMenuKeyDown = (e: React.KeyboardEvent) => {
    const items = [...(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])') ?? [])];
    const idx = items.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      focusItem(idx + 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      focusItem(idx - 1);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setOpen(false);
      (rootRef.current?.querySelector('[aria-haspopup]') as HTMLElement | null)?.focus();
    } else if (e.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      {trigger({
        'aria-haspopup': 'menu',
        'aria-expanded': open,
        'aria-controls': menuId,
        onClick: () => setOpen((o) => !o),
        onKeyDown: (e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setOpen(true);
          }
        },
      })}
      {open && (
        <div
          id={menuId}
          ref={menuRef}
          role="menu"
          onKeyDown={onMenuKeyDown}
          className={cn(
            'absolute z-40 mt-2 min-w-48 rounded-xl border border-line bg-bg-2 p-1 shadow-pop',
            align === 'end' ? 'right-0' : 'left-0',
            menuClassName,
          )}
        >
          <CloseCtx.Provider value={close}>{children}</CloseCtx.Provider>
        </div>
      )}
    </div>
  );
}

const itemClass = (tone?: 'danger') =>
  cn(
    'flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm outline-none transition-colors',
    'focus:bg-surface hover:bg-surface',
    tone === 'danger' ? 'text-danger' : 'text-ink',
  );

export function DropdownItem({
  onSelect,
  to,
  icon,
  children,
  tone,
  disabled,
}: {
  onSelect?: () => void;
  to?: string;
  icon?: ReactNode;
  children: ReactNode;
  tone?: 'danger';
  disabled?: boolean;
}) {
  const close = useContext(CloseCtx);
  const content = (
    <>
      {icon && <span className="text-muted [&>svg]:size-4">{icon}</span>}
      {children}
    </>
  );
  if (to && !disabled) {
    return (
      <Link role="menuitem" to={to} className={itemClass(tone)} onClick={close} tabIndex={-1}>
        {content}
      </Link>
    );
  }
  return (
    <button
      role="menuitem"
      type="button"
      tabIndex={-1}
      aria-disabled={disabled || undefined}
      className={cn(itemClass(tone), disabled && 'pointer-events-none opacity-50')}
      onClick={() => {
        close();
        onSelect?.();
      }}
    >
      {content}
    </button>
  );
}

export const DropdownSeparator = () => <div role="separator" className="my-1 h-px bg-line" />;

export const DropdownLabel = ({ children }: { children: ReactNode }) => (
  <div className="px-3 pb-1 pt-2 text-xs font-medium text-faint">{children}</div>
);

/** The "⋯" contextual actions menu. */
export function ActionsMenu({ label = 'More actions', children }: { label?: string; children: ReactNode }) {
  return (
    <Dropdown
      trigger={(props) => (
        <button
          type="button"
          aria-label={label}
          title={label}
          className="inline-flex size-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface hover:text-ink"
          {...props}
        >
          <MoreHorizontal className="size-4" />
        </button>
      )}
    >
      {children}
    </Dropdown>
  );
}
