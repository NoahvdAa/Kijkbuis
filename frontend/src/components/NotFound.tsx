import {Link} from '@tanstack/react-router';
import {buttonClassName} from './Button';

/**
 * What a page that doesn't exist shows: the teletext page that never comes, with a way back to the TV.
 */
export function NotFound() {
	return <div className="grid justify-items-start gap-6">
		<p className="font-mono text-6xl font-bold tracking-[0.2em] text-teletext-yellow sm:text-7xl">P404</p>
		<div className="grid gap-2">
			<p className="text-lg">This page isn't being broadcast.</p>
			<p className="text-muted">It may have moved, or the link is wrong.</p>
		</div>
		<Link to="/" className={buttonClassName}>Back to the TV</Link>
	</div>;
}
