import {useCallback, useRef} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {CaptionsIcon, CaptionsOffIcon} from './Icons';
import {pickerButtonClassName, pickerMenuClassName, pickerOptionClassName} from './QualityPicker';

type CaptionsPickerProps = {
    /** The channel's sets of captions */
    sets: string[]
    /** The set that shows, or null if captions are off */
    selected: string | null
    /** The number of the screen, in multiview */
    screen: number | null
    open: boolean
    onOpenChange: (open: boolean) => void
    /** Gets the set to show, or null to turn captions off */
    onPick: (set: string | null) => void
};

/**
 * Picks which of a channel's sets of captions shows on one screen, or turns them off. It opens upwards from the control
 * bar, like the quality picker. A channel with one set doesn't need this, an on/off button does the job there.
 */
export function CaptionsPicker({sets, selected, screen, open, onOpenChange, onPick}: CaptionsPickerProps) {
    const container = useRef<HTMLDivElement>(null);
    const close = useCallback(() => onOpenChange(false), [onOpenChange]);
    useDismiss(open, container, close);

    const buttonLabel = `${screen !== null ? `Screen ${screen} captions` : 'Captions'}: ${selected ?? 'off'} (c)`;

    return <div ref={container} className="relative">
        <button
            type="button"
            aria-label={buttonLabel}
            title={buttonLabel}
            aria-expanded={open}
            aria-haspopup="dialog"
            onClick={() => onOpenChange(!open)}
            className={`${pickerButtonClassName} grid place-items-center ${open ? 'bg-fg/15' : ''}`}
        >
            {selected !== null ? <CaptionsIcon/> : <CaptionsOffIcon/>}
        </button>

        {open && <div role="dialog" aria-label="Captions" className={pickerMenuClassName}>
            {[null, ...sets].map((set) => <button
                key={set ?? 'off'}
                type="button"
                aria-pressed={set === selected}
                onClick={() => {
                    onPick(set);
                    onOpenChange(false);
                }}
                className={`${pickerOptionClassName} ${set === selected ? 'text-accent' : ''}`}
            >
                {set ?? 'Off'}
            </button>)}
        </div>}
    </div>;
}
