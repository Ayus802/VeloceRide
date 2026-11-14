import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
  className?: string;
  /** Hide on narrow screens (the table also scrolls horizontally). */
  hideOnMobile?: boolean;
}

/** Plain, borderless-row table; scrolls horizontally on small screens. */
export function DataTable<T>({ columns, rows, rowKey, caption }: { columns: Column<T>[]; rows: T[]; rowKey: (row: T) => string; caption: string }) {
  return (
    <div className="-mx-4 overflow-x-auto sm:mx-0">
      <table className="w-full min-w-[640px] border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-line text-[13px] text-faint">
            {columns.map((c) => (
              <th key={c.header} scope="col" className={cn('px-4 py-2.5 font-medium', c.hideOnMobile && 'hidden md:table-cell', c.className)}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row) => (
            <tr key={rowKey(row)} className="align-middle hover:bg-surface/40">
              {columns.map((c) => (
                <td key={c.header} className={cn('px-4 py-3', c.hideOnMobile && 'hidden md:table-cell', c.className)}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function FilterSelect<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: T | '';
  onChange: (v: T | '') => void;
  options: { value: T | ''; label: string }[];
}) {
  return (
    <label className="inline-flex items-center gap-2 text-[13px] text-muted">
      {label}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as T | '')}
        className="h-9 rounded-lg border border-line bg-bg-2 px-2 text-sm text-ink focus:border-accent focus:outline-none"
      >
        {options.map((o) => (
          <option key={o.value || 'all'} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
