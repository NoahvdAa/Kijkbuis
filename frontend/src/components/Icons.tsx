import type {SVGProps} from 'react';

function Icon(props: SVGProps<SVGSVGElement>) {
    return <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.75}
        strokeLinecap="square"
        aria-hidden="true"
        className="size-5"
        {...props}
    />;
}

export function StopIcon() {
    return <Icon>
        <rect x="6" y="6" width="12" height="12" fill="currentColor" stroke="none"/>
    </Icon>;
}

export function SpeakerIcon() {
    return <Icon>
        <path d="M4 9h4l5-4v14l-5-4H4z" fill="currentColor" stroke="none"/>
        <path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/>
    </Icon>;
}

export function SpeakerMutedIcon() {
    return <Icon>
        <path d="M4 9h4l5-4v14l-5-4H4z" fill="currentColor" stroke="none"/>
        <path d="M16.5 9.5l5 5M21.5 9.5l-5 5"/>
    </Icon>;
}

export function FullscreenIcon() {
    return <Icon>
        <path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"/>
    </Icon>;
}

export function ExitFullscreenIcon() {
    return <Icon>
        <path d="M9 4v5H4M20 9h-5V4M15 20v-5h5M4 15h5v5"/>
    </Icon>;
}
