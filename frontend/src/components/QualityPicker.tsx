import {useCallback, useRef} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {AUDIO_QUALITY, AUTO_QUALITY, formatBitrate, profileSuits, qualityName} from '#/lib/profiles';
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
    /** {@link AUTO_QUALITY}, {@link AUDIO_QUALITY} or a profile ID */
    quality: string
    /** The qualities the user may transcode to, none if they may not */
    profiles: Profile[]
    /** The size the channel is shown at, if known */
    sourceWidth: number | null
    sourceHeight: number | null
    /** Whether the channel is too large to transcode, so it plays at its own quality whatever the screen is set to */
    tooLarge: boolean
    /** Whether the channel can play audio only, which takes video to leave out and sound to keep */
    audioOnly: boolean
    /** How many kilobits per second audio only takes, if known */
    audioBitrateKbps: number | null
    /** How many kilobits per second the channel takes at auto quality, if it played at that quality long enough to tell */
    sourceBitrateKbps: number | null
    /** The number of the screen, in multiview */
    screen: number | null
    open: boolean
    onOpenChange: (open: boolean) => void
    onPick: (quality: string) => void
};

/**
 * Picks the quality of one screen, for people whose connection can't keep up with the channel, or who only want to
 * listen. It opens upwards from the control bar, and stays within the screen.
 */
export function QualityPicker({
                                  quality,
                                  profiles,
                                  sourceWidth,
                                  sourceHeight,
                                  tooLarge,
                                  audioOnly,
                                  audioBitrateKbps,
                                  sourceBitrateKbps,
                                  screen,
                                  open,
                                  onOpenChange,
                                  onPick
                              }: QualityPickerProps) {
    const container = useRef<HTMLDivElement>(null);
    const close = useCallback(() => onOpenChange(false), [onOpenChange]);
    useDismiss(open, container, close);

    // A channel that's too large to transcode plays at its own quality, and so does one without sound on a screen set
    // to audio only. The screen keeps what it's set to for the next channel, but it's not what plays on this one.
    const playing = (quality === AUDIO_QUALITY ? audioOnly : !tooLarge) ? quality : AUTO_QUALITY;
    // Qualities above the channel's own are left out. The current one stays, even if it's above this channel (it was
    // picked on another one), so it's clear what the screen is set to.
    const offered = tooLarge ? [] : profiles.filter((profile) => profile.id === quality || profileSuits(profile, sourceWidth, sourceHeight));
    if (offered.length === 0 && !audioOnly && quality === AUTO_QUALITY && !tooLarge) {
        // The channel is smaller than every quality and can't play audio only, so there's nothing to pick
        return null;
    }

    const label = qualityName(playing, profiles);
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
        ...(audioOnly ? [{
            id: AUDIO_QUALITY,
            name: qualityName(AUDIO_QUALITY, profiles),
            detail: audioBitrateKbps !== null ? formatBitrate(audioBitrateKbps) : 'No picture',
            bitrate: audioBitrateKbps
        }] : []),
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
            className={`${pickerButtonClassName} whitespace-nowrap ${open ? 'bg-fg/15' : ''}`}
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
                aria-pressed={option.id === playing}
                onClick={() => {
                    // Picking what already plays shouldn't change what the screen is set to, on a channel that can't
                    // play that
                    if (option.id !== playing) {
                        onPick(option.id);
                    }
                    onOpenChange(false);
                }}
                className={`${pickerOptionClassName} ${option.id === playing ? 'text-accent' : ''}`}
            >
                {option.name}
                <span className="normal-case tracking-normal text-muted text-right">
                    {option.detail}
                    {option.bitrate !== null ? <>
                        <br/>
                        <small>
                            {/* Auto is what the channel took so far, the others are capped at their bitrate, and sound always takes the same */}
                            ({option.id === AUTO_QUALITY ? `~${formatBitrate(option.bitrate)}=~` : option.id === AUDIO_QUALITY ? '~' : 'max ~'}
                            {formatHourlyUsage(option.bitrate)})
                        </small>
                    </> : null}
                </span>
            </button>)}
        </div>}
    </div>;
}

/**
 * @return what an hour at the bitrate takes, such as 1.4 GB/h, or 58 MB/h for what's too little to tell in gigabytes
 */
function formatHourlyUsage(kbps: number): string {
    const megabytes = kbps * 1000 / 8 * 3600 / 1e6;
    return megabytes < 100
        ? `${Math.round(megabytes)} MB/h`
        : `${(megabytes / 1000).toLocaleString(undefined, {maximumFractionDigits: 1})} GB/h`;
}
