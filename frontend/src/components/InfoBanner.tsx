import {formatTime, progressOf, useNow} from '#/lib/time';
import type {Channel} from '#/lib/types';
import {ChannelLogo} from './ChannelLogo';
import {EpgImage} from './EpgImage';

type InfoBannerProps = {
	channel: Channel
	visible: boolean
	/** Whether the control bar is showing, which the banner then sits on top of */
	raised: boolean
};

/**
 * What's on, shown with the controls for a few seconds after switching channels, like the banner of a TV's set-top
 * box. Large screens get the programme's poster and what's on next, small ones (such as in multiview) only the
 * essentials.
 */
export function InfoBanner({channel, visible, raised}: InfoBannerProps) {
	const now = useNow();
	const programme = channel.now;

	return <div
		aria-hidden={!visible}
		className={`pointer-events-none absolute inset-x-0 z-[5] transition-[opacity,bottom] duration-300 motion-reduce:transition-none ${visible ? 'opacity-100' : 'opacity-0'} ${raised ? 'bottom-12' : 'bottom-0'}`}
	>
		<div className="flex items-end gap-[2.5cqw] bg-screen/85 p-[max(0.5rem,2.5cqw)]">
			{programme?.posterUrl
				? <EpgImage src={programme.posterUrl} className="hidden aspect-[2/3] h-[clamp(5rem,15cqw,10rem)] shrink-0 bg-raised object-cover @md:block"/>
				: programme?.iconUrl
					? <EpgImage src={programme.iconUrl} className="hidden h-auto max-h-[clamp(4rem,13cqw,8rem)] w-auto max-w-[25cqw] shrink-0 object-contain @md:block"/>
					: <ChannelLogo channel={channel} className="hidden size-[clamp(3rem,8cqw,5rem)] shrink-0 @md:grid"/>}

			<div className="grid min-w-0 flex-1 gap-[max(0.25rem,0.8cqw)]">
				<p className="truncate font-mono text-[clamp(0.55rem,1.6cqw,0.875rem)] uppercase tracking-[0.2em] text-muted">
					{channel.channelNumber !== null && <span className="text-accent">{channel.channelNumber} </span>}
					{channel.name}
				</p>

				{programme
					? <>
						<p className="truncate text-[clamp(0.8rem,3cqw,1.75rem)] font-medium leading-tight">{programme.title}</p>
						{programme.subtitle && <p className="hidden truncate text-[clamp(0.7rem,1.8cqw,1rem)] @md:block">{programme.subtitle}</p>}
						<div className="flex items-center gap-[1.5cqw]">
							<span className="shrink-0 font-mono text-[clamp(0.55rem,1.5cqw,0.8rem)] text-muted tabular-nums">
								{formatTime(programme.startsAt)}–{formatTime(programme.endsAt)}
							</span>
							<div className="h-1 flex-1 bg-line">
								<div className="h-full bg-accent" style={{width: `${progressOf(programme, now) * 100}%`}}/>
							</div>
						</div>
					</>
					: <p className="text-[clamp(0.7rem,2cqw,1rem)] text-muted">No programme information</p>}

				{channel.next && <p className="hidden truncate font-mono text-[clamp(0.55rem,1.4cqw,0.8rem)] text-muted @md:block">
					<span className="uppercase tracking-[0.2em]">Next</span>
					<span className="tabular-nums"> {formatTime(channel.next.startsAt)}</span>
					<span className="font-sans"> {channel.next.title}</span>
				</p>}
			</div>
		</div>
	</div>;
}
