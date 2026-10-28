import type {ButtonHTMLAttributes} from 'react';

/**
 * Buttons look like the on-screen menus of an old TV. Also usable on links.
 */
export const buttonClassName = 'inline-flex items-center justify-center gap-2 border border-fg/70 px-5 py-2.5 font-mono text-sm uppercase '
	+ 'tracking-[0.2em] text-fg transition-colors hover:bg-fg hover:text-screen focus-visible:outline-2 focus-visible:outline-offset-2 '
	+ 'focus-visible:outline-accent disabled:pointer-events-none disabled:opacity-50';

/** A smaller button, for actions in lists and forms */
export const smallButtonClassName = buttonClassName.replace('px-5 py-2.5', 'px-3 py-1.5').replace('text-sm', 'text-xs');

/** For actions that can't be undone, such as deleting */
const dangerClassName = 'border-teletext-red! text-teletext-red! hover:bg-teletext-red! hover:text-screen!';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
	small?: boolean
	danger?: boolean
};

export function Button({small = false, danger = false, className = '', ...props}: ButtonProps) {
	return <button
		type="button"
		className={`${small ? smallButtonClassName : buttonClassName} ${danger ? dangerClassName : ''} ${className}`}
		{...props}
	/>;
}
