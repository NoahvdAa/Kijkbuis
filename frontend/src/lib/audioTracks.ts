/** One of the audio tracks of a stream, which the viewer can switch between */
export type AudioTrack = {
    /** The language the channel labels it with, such as dut or eng, if it does */
    language: string | null
    /** Whether it describes what's on screen, for people who can't see it */
    audioDescription: boolean
};

export type AudioTrackLabel = {
    /** Such as Dutch, or Track 2 if the channel doesn't say its language */
    name: string
    /** Such as NL, for the control bar */
    short: string
};

/** Codes for local use, which some broadcasters give a meaning of their own */
const SPECIAL_LANGUAGES: Record<string, string> = {qaa: 'Original language'};

let languageNames: Intl.DisplayNames | null = null;

/**
 * @return what to call each track: its language, or its number if the channel doesn't say. Tracks that would be called
 * the same are numbered, such as when a channel sends a language in stereo and in surround.
 */
export function audioTrackLabels(tracks: AudioTrack[]): AudioTrackLabel[] {
    const labels = tracks.map((track, index) => {
        const language = baseLanguage(track.language);
        const name = language ? SPECIAL_LANGUAGES[language] ?? languageName(language) : null;
        const suffix = track.audioDescription ? ', audio description' : '';
        return {
            name: (name ?? `Audio Track ${index + 1}`) + suffix,
            short: (name && language ? language.toUpperCase() : `${index + 1}`) + (track.audioDescription ? ' AD' : ''),
        };
    });

    const counts = new Map<string, number>();
    labels.forEach((label) => counts.set(label.name, (counts.get(label.name) ?? 0) + 1));
    const numbered = new Map<string, number>();
    return labels.map((label) => {
        if (counts.get(label.name)! === 1) {
            return label;
        }
        const number = (numbered.get(label.name) ?? 0) + 1;
        numbered.set(label.name, number);
        return {name: `${label.name} (${number})`, short: `${label.short} ${number}`};
    });
}

/**
 * @return the language of a code as the browser knows it, such as nl for dut and nld, or null if it isn't one
 */
function baseLanguage(code: string | null): string | null {
    if (!code) {
        return null;
    }
    try {
        return new Intl.Locale(code).language;
    } catch {
        return null;
    }
}

/**
 * @return the name of a language in the page's language, such as Dutch, or null if the browser doesn't know it
 */
function languageName(language: string): string | null {
    try {
        languageNames ??= new Intl.DisplayNames(document.documentElement.lang || 'en', {type: 'language', fallback: 'none'});
        const name = languageNames.of(language);
        return name ? name.charAt(0).toLocaleUpperCase() + name.slice(1) : null;
    } catch {
        return null;
    }
}
