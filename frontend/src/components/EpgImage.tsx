import {useState} from 'react';

/**
 * An image from the EPG, such as a programme's poster. They're on other sites, so one that can't be loaded is left
 * out rather than shown broken.
 */
export function EpgImage({src, lazy = false, className = ''}: {src: string, lazy?: boolean, className?: string}) {
	const [failedSrc, setFailedSrc] = useState<string | null>(null);

	if (failedSrc === src) {
		return null;
	}
	return <img
		src={src}
		// Shown next to the programme's title, so there's nothing to add for screen readers
		alt=""
		// For long lists, where most images are off screen
		loading={lazy ? 'lazy' : undefined}
		// Some image hosts refuse requests that come from other sites
		referrerPolicy="no-referrer"
		onError={() => setFailedSrc(src)}
		className={className}
	/>;
}
