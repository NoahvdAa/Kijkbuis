import type {ReactNode} from 'react';

export const fieldClassName = 'border border-line bg-raised px-3 py-2 text-fg placeholder:text-muted focus-visible:outline-2 '
	+ 'focus-visible:-outline-offset-1 focus-visible:outline-accent';

export const labelClassName = 'font-mono text-xs uppercase tracking-[0.2em] text-muted';

/**
 * A form field with its label above it, and an optional hint below.
 */
export function Field({label, hint, className = '', children}: {label: string, hint?: ReactNode, className?: string, children: ReactNode}) {
	return <label className={`grid content-start gap-1.5 ${className}`}>
		<span className={labelClassName}>{label}</span>
		{children}
		{hint && <span className="text-sm text-muted">{hint}</span>}
	</label>;
}
