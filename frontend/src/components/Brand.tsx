const KEYS = ['bg-teletext-red', 'bg-teletext-green', 'bg-teletext-yellow', 'bg-teletext-blue'];

/**
 * The Kijkbuis wordmark, over the four coloured keys of a TV remote.
 */
export function Brand({large = false}: {large?: boolean}) {
	return <div className="inline-grid gap-2">
		<span className={`-mr-[0.35em] font-mono font-bold uppercase tracking-[0.35em] ${large ? 'text-4xl sm:text-5xl' : 'text-sm'}`}>
			Kijkbuis
		</span>
		<div className={`grid grid-cols-4 ${large ? 'h-1.5 gap-1.5' : 'h-1 gap-1'}`} aria-hidden="true">
			{KEYS.map((key) => <span key={key} className={key}/>)}
		</div>
	</div>;
}
