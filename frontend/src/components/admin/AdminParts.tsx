import {type ReactNode, useState} from 'react';
import type {Status} from '#/lib/admin';
import {Button} from '../Button';

const LIGHT_COLORS: Record<Status, string> = {
	ok: 'bg-teletext-green',
	busy: 'animate-pulse bg-teletext-yellow',
	error: 'bg-teletext-red',
	off: 'bg-line',
};

/**
 * A small coloured light, like the ones on the front of a TV: green when all is well, yellow while busy, red when
 * something's wrong.
 */
export function StatusLight({status, label}: {status: Status, label: string}) {
	return <span role="img" aria-label={label} title={label} className={`inline-block size-2.5 shrink-0 ${LIGHT_COLORS[status]}`}/>;
}

/**
 * A part of an admin page, with a title and room for buttons next to it.
 */
export function AdminSection({title, actions, children}: {title: ReactNode, actions?: ReactNode, children: ReactNode}) {
	return <section className="grid gap-5">
		<div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
			<h2 className="font-mono text-sm uppercase tracking-[0.25em]">{title}</h2>
			{actions && <div className="flex flex-wrap gap-2">{actions}</div>}
		</div>
		{children}
	</section>;
}

/**
 * A small label, such as a stream's codec.
 */
export function Badge({accent = false, children}: {accent?: boolean, children: ReactNode}) {
	return <li className={`border px-1.5 py-0.5 font-mono text-xs uppercase tracking-[0.1em] ${accent ? 'border-accent/60 text-accent' : 'border-line text-muted'}`}>
		{children}
	</li>;
}

/**
 * A button for something that can't be undone, which asks for a second click first.
 */
export function ConfirmButton({label, confirmLabel, disabled, onConfirm}: {label: string, confirmLabel: string, disabled?: boolean, onConfirm: () => void}) {
	const [confirming, setConfirming] = useState(false);

	if (!confirming) {
		return <Button small danger disabled={disabled} onClick={() => setConfirming(true)}>{label}</Button>;
	}
	return <span className="inline-flex flex-wrap gap-2">
		<Button small danger autoFocus onClick={() => {
			setConfirming(false);
			onConfirm();
		}}>{confirmLabel}</Button>
		<Button small onClick={() => setConfirming(false)}>Cancel</Button>
	</span>;
}

/**
 * Says what went wrong, for screen readers too.
 */
export function ErrorText({children}: {children: ReactNode}) {
	return <p role="alert" className="text-sm text-teletext-red">{children}</p>;
}
