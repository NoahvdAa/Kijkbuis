import {useCallback, useRef} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {AUTO_QUALITY, formatBitrate, profileSuits} from '#/lib/profiles';
import type {Profile} from '#/lib/types';

/** The button that opens a picker in a screen's control bar, such as the quality or audio picker */
export const pickerButtonClassName = 'h-9 px-2 font-mono text-xs uppercase tracking-[0.15em] hover:bg-fg/15 focus-visible:outline-2 '
    + 'focus-visible:-outline-offset-2 focus-visible:outline-accent';
/** A picker's menu, which fits above the control bar, and scrolls on screens too small for every option */
export const pickerMenuClassName = 'absolute right-0 bottom-full z-30 mb-1.5 grid max-h-[calc(100cqh-3.5rem)] min-w-52 overflow-y-auto border '
    + 'border-line bg-panel py-1 shadow-2xl';
/** An option in a picker's menu */
export const pickerOptionClassName = 'flex items-baseline justify-between gap-6 px-3 py-1.5 text-left font-mono text-xs uppercase tracking-[0.15em] '
    + 'hover:bg-fg/15 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent';

type QualityPickerProps = {
    /** {@link AUTO_QUALITY} or a profile ID */
    quality: string
    profiles: Profile[]
    /** The size the channel is shown at, if known */
    sourceWidth: number | null
    sourceHeight: number | null
    /** How many kilobits per second the channel takes at auto quality, if it played at that quality long enough to tell */
    sourceBitrateKbps: number | null
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
export function QualityPicker({
                                  quality,
                                  profiles,
                                  sourceWidth,
                                  sourceHeight,
                                  sourceBitrateKbps,
                                  screen,
                                  open,
                                  onOpenChange,
                                  onPick
                              }: QualityPickerProps) {
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
        {
            id: AUTO_QUALITY,
            name: 'Auto',
            detail: sourceWidth !== null && sourceHeight !== null ? `Source ${sourceWidth}×${sourceHeight}` : 'Source',
            bitrate: sourceBitrateKbps
        },
        ...offered.map((profile) => ({
            id: profile.id,
            name: profile.name,
            detail: formatBitrate(profile.bitrateKbps),
            bitrate: profile.bitrateKbps
        })),
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
            className={`${pickerButtonClassName} ${open ? 'bg-fg/15' : ''}`}
        >
            {label}
        </button>

        {open && <div
            role="dialog"
            aria-label="Quality"
            className={pickerMenuClassName}
        >
            {options.map((option) => <button
                key={option.id}
                type="button"
                aria-pressed={option.id === quality}
                onClick={() => {
                    onPick(option.id);
                    onOpenChange(false);
                }}
                className={`${pickerOptionClassName} ${option.id === quality ? 'text-accent' : ''}`}
            >
                {option.name}
                <span className="normal-case tracking-normal text-muted text-right">
                    {option.detail}
                    {option.bitrate !== null ? <>
                        <br/>
                        <small>
                            {/* Auto is what the channel took so far, the others are capped at their bitrate */}
                            ({option.id === AUTO_QUALITY ? `~${formatBitrate(option.bitrate)}=~` : 'max ~'}
                            {hourlyUsage(option.bitrate).toLocaleString(undefined, {maximumFractionDigits: 1})} GB/h)
                        </small>
                    </> : null}
                </span>
            </button>)}
        </div>}
    </div>;
}

/**
 * @return how many gigabytes an hour at the bitrate takes
 */
function hourlyUsage(kbps: number): number {
    return kbps * 1000 / 8 * 3600 / 1e9;
}
