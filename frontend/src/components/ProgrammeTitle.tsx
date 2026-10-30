import type {Programme} from '#/lib/types';

/**
 * A programme's title, followed by its episode title if the EPG has one. That one's dimmed, so the title stands out.
 */
export function ProgrammeTitle({programme}: {programme: Programme}) {
	const episode = episodeOf(programme);
	return <>
		{programme.title}
		{episode && <span className="text-muted"> · {episode}</span>}
	</>;
}

/**
 * @return the programme's title and episode title as plain text, such as for a label
 */
export function programmeTitleText(programme: Programme): string {
	const episode = episodeOf(programme);
	return episode ? `${programme.title}, ${episode}` : programme.title;
}

/**
 * @return the episode title, if the EPG has one. Some repeat the programme's title there, which isn't one.
 */
export function episodeOf(programme: Programme): string | null {
	return programme.subtitle !== programme.title ? programme.subtitle : null;
}
