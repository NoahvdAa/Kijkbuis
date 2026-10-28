import {useEffect, useState} from 'react';

/**
 * The current time, updated on the minute.
 */
export function Clock({className = ''}: {className?: string}) {
	const [now, setNow] = useState(() => new Date());

	useEffect(() => {
		let timeout: number;
		const tick = () => {
			setNow(new Date());
			timeout = window.setTimeout(tick, 60_000 - Date.now() % 60_000);
		};
		timeout = window.setTimeout(tick, 60_000 - Date.now() % 60_000);
		return () => window.clearTimeout(timeout);
	}, []);

	return <time className={`font-mono tabular-nums ${className}`} dateTime={now.toISOString()}>
		{now.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}
	</time>;
}
