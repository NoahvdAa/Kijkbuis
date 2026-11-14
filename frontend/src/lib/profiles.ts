import {useEffect, useState} from 'react';
import {api} from './api';
import type {Profile} from './types';

/** Keeps the channel's own quality, and only transcodes when the browser needs that */
export const AUTO_QUALITY = 'auto';
/** Leaves the video out. Nothing is transcoded for it, so it doesn't take being allowed to pick a quality. */
export const AUDIO_QUALITY = 'audio';

let profiles: Promise<Profile[]> | null = null;

/**
 * Loads the qualities that can be picked. They come from the backend's config, so they're only loaded once.
 *
 * @param enabled whether the user may pick a quality at all
 * @return the qualities, or nothing while they load or if the user may not pick one
 */
export function useProfiles(enabled: boolean): Profile[] {
    const [loaded, setLoaded] = useState<Profile[]>([]);

    useEffect(() => {
        if (!enabled) {
            return;
        }
        let cancelled = false;
        profiles ??= api<Profile[]>('/api/profiles').catch(() => {
            // Try again next time, there's just no choice until then
            profiles = null;
            return [];
        });
        profiles.then((result) => !cancelled && setLoaded(result));
        return () => {
            cancelled = true;
        };
    }, [enabled]);

    return enabled ? loaded : [];
}

/**
 * @return whether a profile is worth picking for a channel of the given size, which is when it scales the channel
 * down, or keeps it the same size at a capped bitrate. A larger profile only costs a transcode, and doesn't add
 * detail. The size is the size the channel is shown at, so it works for any aspect ratio. For portrait channels the
 * profile's box is on its side, which is how the backend scales them.
 */
export function profileSuits(profile: Profile, sourceWidth: number | null, sourceHeight: number | null): boolean {
    if (sourceWidth === null || sourceHeight === null) {
        return true;
    }
    // The box's long side goes with the channel's long side
    return Math.max(sourceWidth, sourceHeight) >= profile.width || Math.min(sourceWidth, sourceHeight) >= profile.height;
}

/**
 * @param quality {@link AUTO_QUALITY}, {@link AUDIO_QUALITY} or a profile ID
 * @return what to call the quality
 */
export function qualityName(quality: string, profiles: Profile[]): string {
    if (quality === AUTO_QUALITY) {
        return 'Auto';
    }
    if (quality === AUDIO_QUALITY) {
        return 'Audio only';
    }
    return profiles.find((profile) => profile.id === quality)?.name ?? quality;
}

/**
 * @return the bitrate for people, such as 700 kbps or 1.2 Mbps
 */
export function formatBitrate(kbps: number): string {
    return kbps < 1000 ? `${kbps} kbps` : `${(kbps / 1000).toLocaleString(undefined, {maximumFractionDigits: 1})} Mbps`;
}
