import {useCallback, useRef} from 'react';
import {type AudioTrack, audioTrackLabels} from '#/lib/audioTracks';
import {useDismiss} from '#/lib/dismiss';
import {pickerButtonClassName, pickerMenuClassName, pickerOptionClassName} from './QualityPicker';

type AudioPickerProps = {
    tracks: AudioTrack[]
    /** The index of the track that plays */
    selected: number
    /** The number of the screen, in multiview */
    screen: number | null
    open: boolean
    onOpenChange: (open: boolean) => void
    onPick: (index: number) => void
};

/**
 * Picks which of a channel's audio tracks plays on one screen, such as another language or audio description. It opens
 * upwards from the control bar, like the quality picker.
 */
export function AudioPicker({tracks, selected, screen, open, onOpenChange, onPick}: AudioPickerProps) {
    const container = useRef<HTMLDivElement>(null);
    const close = useCallback(() => onOpenChange(false), [onOpenChange]);
    useDismiss(open, container, close);

    const labels = audioTrackLabels(tracks);
    const current = labels[selected] ?? labels[0];
    const buttonLabel = `${screen !== null ? `Screen ${screen} audio` : 'Audio'}: ${current.name}`;

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
            {current.short}
        </button>

        {open && <div role="dialog" aria-label="Audio" className={pickerMenuClassName}>
            {labels.map((label, index) => <button
                key={index}
                type="button"
                aria-pressed={index === selected}
                onClick={() => {
                    onPick(index);
                    onOpenChange(false);
                }}
                className={`${pickerOptionClassName} ${index === selected ? 'text-accent' : ''}`}
            >
                {label.name}
            </button>)}
        </div>}
    </div>;
}
