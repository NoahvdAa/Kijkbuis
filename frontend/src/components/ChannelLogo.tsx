import {useState} from 'react';
import type {Channel} from '#/lib/types';

/**
 * A channel's logo, or its initials when it has none or the logo can't be loaded.
 */
export function ChannelLogo({channel, className = ''}: {channel: Pick<Channel, 'name' | 'logoUrl'>, className?: string}) {
	const [failed, setFailed] = useState(false);

	return <div className={`grid aspect-square place-items-center overflow-hidden bg-raised ${className}`}>
		{channel.logoUrl && !failed
			// The name is always shown next to the logo, so the logo itself doesn't need to be read out
			? <img src={channel.logoUrl} alt="" loading="lazy" className="size-full object-contain p-1" onError={() => setFailed(true)}/>
			: <span aria-hidden="true" className="font-mono text-xs uppercase text-muted">{initials(channel.name)}</span>}
	</div>;
}

function initials(name: string): string {
	return name.split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]).join('');
}
