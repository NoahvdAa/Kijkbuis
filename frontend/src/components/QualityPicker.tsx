import {useCallback, useRef} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {AUTO_QUALITY, formatBitrate, profileSuits} from '#/lib/profiles';
import type {Profile} from '#/lib/types';

type QualityPickerProps = {
	/** {@link AUTO_QUALITY} or a profile ID */
	quality: string
	profiles: Profile[]
	/** The size the channel is shown at, if known */
	sourceWidth: number | null
	sourceHeight: number | null
	/** The number of the screen, in multiview */
	screen: number | null
	open: boolean
	onOpenChange: (open: boolean) => void
	onPick: (quality: string) => void
};

/**
 * Picks the quality of one screen, for people whose connection can't keep up with the channel. It opens upwards from
 * the control bar, and stays within the screen.
 */
export function QualityPicker({quality, profiles, sourceWidth, sourceHeight, screen, open, onOpenChange, onPick}: QualityPickerProps) {
	const container = useRef<HTMLDivElement>(null);
	const close = useCallback(() => onOpenChange(false), [onOpenChange]);
	useDismiss(open, container, close);

	// Qualities above the channel's own are left out. The current one stays, even if it's above this channel (it was
	// picked on another one), so it's clear what the screen is set to.
	const offered = profiles.filter((profile) => profile.id === quality || profileSuits(profile, sourceWidth, sourceHeight));
	if (offered.length === 0 && quality === AUTO_QUALITY) {
		// The channel is smaller than every quality, so there's nothing to pick
		return null;
	}

	const label = profiles.find((profile) => profile.id === quality)?.name ?? 'Auto';
	const options = [
		{id: AUTO_QUALITY, name: 'Auto', detail: sourceWidth !== null && sourceHeight !== null ? `Source ${sourceWidth}×${sourceHeight}` : 'Source'},
		...offered.map((profile) => ({id: profile.id, name: profile.name, detail: formatBitrate(profile.bitrateKbps)})),
	];
	const buttonLabel = `${screen !== null ? `Screen ${screen} quality` : 'Quality'}: ${label}`;

	return <div ref={container} className="relative">
		<button
			type="button"
			aria-label={buttonLabel}
			title={buttonLabel}
			aria-expanded={open}
			aria-haspopup="dialog"
			onClick={() => onOpenChange(!open)}
			className={`h-9 px-2 font-mono text-xs uppercase tracking-[0.15em] hover:bg-fg/15 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent ${open ? 'bg-fg/15' : ''}`}
		>
			{label}
		</button>

		{open && <div
			role="dialog"
			aria-label="Quality"
			// Fits above the control bar, and scrolls on screens too small for every option
			className="absolute right-0 bottom-full z-30 mb-1.5 grid max-h-[calc(100cqh-3.5rem)] min-w-52 overflow-y-auto border border-line bg-panel py-1 shadow-2xl"
		>
			{options.map((option) => <button
				key={option.id}
				type="button"
				aria-pressed={option.id === quality}
				onClick={() => {
					onPick(option.id);
					onOpenChange(false);
				}}
				className={`flex items-baseline justify-between gap-6 px-3 py-1.5 text-left font-mono text-xs uppercase tracking-[0.15em] hover:bg-fg/15 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent ${option.id === quality ? 'text-accent' : ''}`}
			>
				{option.name}
				<span className="normal-case tracking-normal text-muted">{option.detail}</span>
			</button>)}
		</div>}
	</div>;
}
