import Hls from 'hls.js';
import {useEffect, useState} from 'react';

/** A codec string per video codec the backend can pass through, to ask the browser about */
const CODEC_PROBES: Record<string, string> = {
    h264: 'avc1.640028',
    hevc: 'hvc1.1.6.L120.90',
    av1: 'av01.0.08M.08',
};

let playableCodecs: string[] | null = null;

/**
 * The video codecs this browser can play. Sent along when starting a stream, so the backend transcodes channels the
 * browser can't play.
 */
export function playableVideoCodecs(): string[] {
    if (!playableCodecs) {
        const mediaSource = Hls.getMediaSource();
        const video = document.createElement('video');
        playableCodecs = Object.entries(CODEC_PROBES)
            .filter(([, codec]) => {
                const type = `video/mp4; codecs="${codec}"`;
                return mediaSource?.isTypeSupported(type) || video.canPlayType(type) === 'probably';
            })
            .map(([name]) => name);
        if (playableCodecs.length === 0) {
            // Every browser plays H.264, even if it won't say so
            playableCodecs = ['h264'];
        }
    }
    return playableCodecs;
}

/**
 * Whether this is a phone or tablet. Their browsers only play one video with sound at a time, and set the volume
 * with the device's buttons rather than letting the page do it.
 */
export const singleAudioSource: boolean = (() => {
    const userAgent = navigator.userAgent;
    const mobileHint = (navigator as Navigator & {
        userAgentData?: { mobile: boolean }
    }).userAgentData?.mobile ?? false;
    // iPads pretend to be Macs, but Macs don't have touch screens
    const iPad = /Macintosh/.test(userAgent) && navigator.maxTouchPoints > 1;
    return mobileHint || iPad || /Android|iPhone|iPad|iPod/i.test(userAgent);
})();

/**
 * @return the element that's fullscreen, if any
 */
export function useFullscreenElement(): Element | null {
    const [element, setElement] = useState(() => document.fullscreenElement);

    useEffect(() => {
        const onChange = () => setElement(document.fullscreenElement);
        document.addEventListener('fullscreenchange', onChange);
        return () => document.removeEventListener('fullscreenchange', onChange);
    }, []);

    return element;
}

/**
 * Whether the page can show elements fullscreen. Notably not on iPhones.
 */
export function canFullscreenPage(): boolean {
    return document.fullscreenEnabled;
}

/**
 * Whether a video can be shown fullscreen, which iPhones only allow for the video element itself.
 */
export function canFullscreenVideo(): boolean {
    return document.fullscreenEnabled || 'webkitEnterFullscreen' in HTMLVideoElement.prototype;
}

/**
 * Shows an element fullscreen, or leaves fullscreen if it already is.
 *
 * @param video the video to show fullscreen instead, on devices that only allow that (iPhones)
 */
export function toggleFullscreen(element: Element, video?: HTMLVideoElement | null): void {
    if (document.fullscreenElement === element) {
        document.exitFullscreen().catch(() => {
            // Already left fullscreen
        });
    } else if (document.fullscreenEnabled) {
        element.requestFullscreen().catch(() => {
            // The browser refused, e.g. because it wasn't a direct reaction to the user
        });
    } else {
        (video as HTMLVideoElement & {
            webkitEnterFullscreen?: () => void
        } | null | undefined)?.webkitEnterFullscreen?.();
    }
}

const CODEC_NAMES: Record<string, string> = {
    h264: 'H.264',
    hevc: 'HEVC',
    av1: 'AV1',
    mpeg2video: 'MPEG-2',
    vp9: 'VP9',
};

/**
 * @return a codec's name for people, from the name ffmpeg uses
 */
export function codecName(codec: string): string {
    return CODEC_NAMES[codec] ?? codec.toUpperCase();
}
