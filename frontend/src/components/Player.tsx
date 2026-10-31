import Hls from 'hls.js';
import {type ReactNode, useCallback, useEffect, useRef, useState} from 'react';
import {api, ApiError} from '#/lib/api';
import {canFullscreenVideo, playableVideoCodecs, singleAudioSource, toggleFullscreen, useFullscreenElement} from '#/lib/media';
import {type Tile, useTv} from '#/lib/tv';
import type {Channel, Profile} from '#/lib/types';
import {ExitFullscreenIcon, FullscreenIcon, SpeakerIcon, SpeakerMutedIcon, StopIcon} from './Icons';
import {InfoBanner} from './InfoBanner';
import {newPlayerDebug, PlayerStats, resetPlayerDebug} from './PlayerStats';
import {QualityPicker} from './QualityPicker';

/** What the backend responds with when starting a stream */
export type StreamInfo = {
	id: string
	playlistUrl: string
	/** auto or a profile ID */
	quality: string
	transcoding: boolean
	sourceVideoCodec: string | null
	sourceInterlaced: boolean
	/** The size the channel is shown at, if known */
	sourceWidth: number | null
	sourceHeight: number | null
};

type StreamState =
	| {status: 'off'}
	| {status: 'tuning'}
	| {status: 'playing', stream: StreamInfo}
	| {status: 'failed', message: string};

/** How often a stream that drops is restarted before giving up */
const MAX_RESTARTS = 3;
/** How long playback has to be stuck before the tuning bar shows, so short hiccups don't make it flash */
const STALL_DELAY = 500;
/** How long the info banner stays at most after the channel started, or after it was brought up */
const BANNER_TIME = 6_000;

/**
 * Whether the video is waiting for its first frame, playing, or stuck waiting for more of the stream (e.g. when the
 * channel falls behind).
 */
type Playback = 'starting' | 'playing' | 'stalled';

type PlayerProps = {
	tile: Tile
	/** The number of the screen, shown in multiview */
	number: number
	multiview: boolean
	/** Whether the TV is in the corner, where it doesn't show controls */
	pip: boolean
	showControls: boolean
	/** Whether the TV's controls are showing, on any screen */
	tvAwake: boolean
	/** Whether this is the screen the keyboard controls, which is always the case without multiview */
	active: boolean
	/** The qualities the user can pick from, none if they may not */
	profiles: Profile[]
	/** Whether the quality menu is open */
	menuOpen: boolean
	onMenuOpenChange: (open: boolean) => void
	/** Selects this screen, in multiview */
	onSelect: () => void
	/** Shows the TV's controls, as if the mouse moved */
	onWake: () => void
};

/**
 * One screen of the TV: plays the tile's channel, and has minimal controls to stop it, set the volume and quality, and
 * show it fullscreen.
 */
export function Player({tile, number, multiview, pip, showControls, tvAwake, active, profiles, menuOpen, onMenuOpenChange, onSelect, onWake}: PlayerProps) {
	const {stop, setMuted, setVolume, setQuality} = useTv();
	const container = useRef<HTMLDivElement>(null);
	const video = useRef<HTMLVideoElement>(null);
	const channel = useChannel(tile.channelId);
	const [stream, restart, fail] = useStream(tile.channelId, tile.quality);
	const [playback, setPlayback] = useState<Playback>('starting');
	const stalled = useDelayedTrue(playback === 'stalled', STALL_DELAY);
	const fullscreen = useFullscreenElement() === container.current && container.current !== null;
	// For the stats panel, which the d key shows on the selected screen
	const debug = useRef(newPlayerDebug());
	const [statsOpen, setStatsOpen] = useState(false);

	// The info banner comes up with the controls for every new channel (the TV wakes up for those), and when asked
	// for. It's when it was brought up, or null.
	const [bannerShownAt, setBannerShownAt] = useState<number | null>(null);
	useEffect(() => {
		setBannerShownAt(tile.channelId ? Date.now() : null);
	}, [tile.channelId]);
	useEffect(() => {
		// It goes away a while after the channel started, even if the controls stay
		if (bannerShownAt === null || stream.status === 'tuning') {
			return;
		}
		const timeout = window.setTimeout(() => setBannerShownAt(null), BANNER_TIME);
		return () => window.clearTimeout(timeout);
	}, [bannerShownAt, stream.status]);
	useEffect(() => {
		// It's part of the controls, so it goes away with them, and doesn't come back when they do
		if (!showControls) {
			setBannerShownAt(null);
		}
	}, [showControls]);
	const showBanner = bannerShownAt !== null && showControls && !pip && (stream.status === 'tuning' || stream.status === 'playing');

	// The i key brings up the banner of the screen the keyboard controls, or hides it again
	useEffect(() => {
		if (pip || !active || !tile.channelId) {
			return;
		}
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event.target)) {
				return;
			}
			if (event.key === 'd') {
				setStatsOpen((open) => !open);
				return;
			}
			if (event.key !== 'i') {
				return;
			}
			if (bannerShownAt !== null) {
				setBannerShownAt(null);
			} else {
				onWake();
				setBannerShownAt(Date.now());
			}
		};
		window.addEventListener('keydown', onKeyDown);
		return () => window.removeEventListener('keydown', onKeyDown);
	}, [pip, active, tile.channelId, bannerShownAt, onWake]);

	// The latest audio settings, for starting playback without restarting it whenever they change
	const audio = useRef(tile);
	audio.current = tile;

	useEffect(() => {
		if (video.current) {
			video.current.muted = tile.muted;
			video.current.volume = tile.volume;
		}
	}, [tile.muted, tile.volume]);

	const play = useCallback(() => {
		const element = video.current;
		if (!element) {
			return;
		}
		element.muted = audio.current.muted;
		element.volume = audio.current.volume;
		element.play().catch((error: unknown) => {
			if (error instanceof DOMException && error.name === 'NotAllowedError' && !element.muted) {
				// Browsers don't play sound until the user interacted with the page, so start muted instead
				setMuted(audio.current.id, true);
				element.muted = true;
				element.play().catch(() => {
					// Nothing more we can do, the screen shows the first frame
				});
			}
		});
	}, [setMuted]);

	// Attach the stream to the video
	const restarts = useRef(0);
	useEffect(() => {
		const element = video.current;
		if (stream.status !== 'playing' || !element) {
			return;
		}
		setPlayback('starting');

		// The stream may stop, e.g. when the upstream drops or the device slept long enough for the backend to stop
		// it. Start a new one a few times before giving up.
		const lost = () => {
			debug.current.restarts++;
			if (restarts.current++ < MAX_RESTARTS) {
				restart();
			} else {
				fail('The channel stopped playing');
			}
		};

		let hls: Hls | null = null;
		if (Hls.isSupported()) {
			hls = new Hls({backBufferLength: 30, maxBufferLength: 20});
			debug.current.hls = hls;
			hls.on(Hls.Events.MANIFEST_PARSED, play);
			hls.on(Hls.Events.BUFFER_CODECS, (_event, data) => {
				debug.current.videoCodec = data.video?.codec ?? data.audiovideo?.codec ?? null;
				debug.current.audioCodec = data.audio?.codec ?? null;
			});
			hls.on(Hls.Events.FRAG_LOADED, (_event, {frag}) => {
				debug.current.lastSegment = {
					number: typeof frag.sn === 'number' ? frag.sn : 0,
					bytes: frag.stats.loaded,
					seconds: (frag.stats.loading.end - frag.stats.loading.start) / 1000,
					duration: frag.duration,
				};
			});
			hls.on(Hls.Events.ERROR, (_event, data) => {
				debug.current.errors++;
				debug.current.lastError = `${data.details}${data.response?.code ? ` (HTTP ${data.response.code})` : ''}${data.fatal ? ', fatal' : ''}`;
				if (data.response?.code === 410) {
					// An admin stopped the stream, so starting a new one would undo that
					fail('An admin stopped this stream');
					return;
				}
				if (!data.fatal) {
					return;
				}
				if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
					hls?.recoverMediaError();
				} else {
					lost();
				}
			});
			hls.loadSource(stream.stream.playlistUrl);
			hls.attachMedia(element);
		} else {
			// Older iPhones can't use hls.js, but play HLS themselves
			element.src = stream.stream.playlistUrl;
			element.addEventListener('error', lost);
			play();
		}

		return () => {
			hls?.destroy();
			debug.current.hls = null;
			element.removeEventListener('error', lost);
			element.removeAttribute('src');
			element.load();
		};
	}, [stream, play, restart, fail]);

	// A new channel or quality gets its own restarts, and its own stats
	useEffect(() => {
		restarts.current = 0;
		resetPlayerDebug(debug.current);
	}, [tile.channelId, tile.quality]);
	useEffect(() => {
		if (stalled) {
			debug.current.stalls++;
		}
	}, [stalled]);

	return <div ref={container} className="absolute inset-0 overflow-hidden bg-screen [container-type:size]">
		<video
			ref={video}
			playsInline
			className="absolute inset-0 size-full object-contain"
			// Fired when playback runs out of video, such as when the channel falls behind
			onWaiting={() => setPlayback((current) => current === 'starting' ? 'starting' : 'stalled')}
			onPlaying={() => {
				setPlayback('playing');
				restarts.current = 0;
			}}
		/>

		<Status stream={stream} channel={channel} starting={playback === 'starting'} stalled={stalled} onRetry={restart}/>

		{channel && !pip && <InfoBanner channel={channel} visible={showBanner} raised={showControls}/>}

		{statsOpen && !pip && <PlayerStats
			video={video}
			container={container}
			debug={debug}
			channel={channel}
			stream={stream.status === 'playing' ? stream.stream : null}
			status={stream.status === 'failed' ? `failed: ${stream.message}` : stream.status === 'playing' ? playback : stream.status}
			onClose={() => setStatsOpen(false)}
		/>}

		{/* Clicking the picture selects the screen in multiview, and resumes it if the browser paused it */}
		{!pip && <button
			type="button"
			aria-label={multiview ? `Select screen ${number}` : undefined}
			// Without multiview it only catches clicks on the picture, so it's left out for keyboards and screen readers
			aria-hidden={!multiview}
			tabIndex={multiview ? 0 : -1}
			onClick={() => {
				onSelect();
				if (stream.status === 'playing' && video.current?.paused) {
					play();
				}
			}}
			className="absolute inset-0 cursor-default focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
		/>}

		{/*
			Which screen is which only matters while doing something, so the number shows with the controls, but not while
			just watching. A screen showing its control bar has the number in there. The corner TV always shows them, since
			the menus refer to screens by number.
		*/}
		{multiview && <span
			aria-hidden="true"
			className={`pointer-events-none absolute top-[3cqw] left-[3cqw] font-mono text-[max(0.5rem,3.5cqw)] text-muted transition-opacity duration-200 ${pip || (tvAwake && !(showControls && tile.channelId)) ? 'opacity-100' : 'opacity-0'}`}
		>
			{number}
		</span>}

		{!pip && tile.channelId && <div
			className={`absolute inset-x-0 bottom-0 z-10 bg-screen/80 transition-opacity duration-200 ${showControls ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
			inert={!showControls}
		>
			<div className="flex items-center gap-1 px-2 py-1.5 sm:px-3">
				<ControlButton label={multiview ? `Stop screen ${number}` : 'Stop'} onClick={() => stop(tile.id)}>
					<StopIcon/>
				</ControlButton>
				<ControlButton label={tile.muted ? 'Unmute' : 'Mute'} pressed={tile.muted} onClick={() => setMuted(tile.id, !tile.muted)}>
					{tile.muted || tile.volume === 0 ? <SpeakerMutedIcon/> : <SpeakerIcon/>}
				</ControlButton>
				{/* Phones and tablets set the volume with their buttons */}
				{!singleAudioSource && <input
					type="range"
					min={0}
					max={1}
					step={0.05}
					value={tile.muted ? 0 : tile.volume}
					onChange={(event) => setVolume(tile.id, Number(event.target.value))}
					aria-label="Volume"
					className="h-1 w-[min(7rem,18cqw)] cursor-pointer accent-fg"
				/>}

				{/* The banner says the same, so the label makes way for it */}
				<button
					type="button"
					title="Programme info (i)"
					onClick={() => setBannerShownAt(Date.now())}
					className={`ml-2 min-w-0 flex-1 truncate text-left font-mono text-xs uppercase tracking-[0.15em] hover:text-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${showBanner ? 'invisible' : ''}`}
				>
					{multiview && <span className="text-muted">{number} · </span>}
					{channel?.name}
					{channel?.now && <span className="normal-case tracking-normal text-muted"> · {channel.now.title}</span>}
				</button>

				{profiles.length > 0 && <QualityPicker
					quality={tile.quality}
					profiles={profiles}
					sourceWidth={stream.status === 'playing' ? stream.stream.sourceWidth : null}
					sourceHeight={stream.status === 'playing' ? stream.stream.sourceHeight : null}
					screen={multiview ? number : null}
					open={menuOpen}
					onOpenChange={onMenuOpenChange}
					onPick={(quality) => setQuality(tile.id, quality)}
				/>}

				{canFullscreenVideo() && <ControlButton
					label={fullscreen ? 'Exit fullscreen' : multiview ? `Screen ${number} fullscreen` : 'Fullscreen picture'}
					onClick={() => container.current && toggleFullscreen(container.current, video.current)}
				>
					{fullscreen ? <ExitFullscreenIcon/> : <FullscreenIcon/>}
				</ControlButton>}
			</div>
		</div>}
	</div>;
}

type StatusProps = {
	stream: StreamState
	channel: Channel | null
	/** Whether the video is waiting for its first frame */
	starting: boolean
	/** Whether playback has been stuck for a moment */
	stalled: boolean
	onRetry: () => void
};

/**
 * Covers the picture with what's going on, while there's no picture or it's stuck.
 */
function Status({stream, channel, starting, stalled, onRetry}: StatusProps) {
	let content: ReactNode;
	if (stream.status === 'off') {
		content = <p className="-mr-[0.4em] font-mono text-[clamp(0.5rem,3.5cqw,1.5rem)] uppercase tracking-[0.4em] text-muted">NO SOURCE SELECTED</p>;
	} else if (stream.status === 'failed') {
		content = <div className="relative z-10 grid justify-items-center gap-[2cqw]">
			<p className="text-[clamp(0.6rem,2.5cqw,1rem)] text-muted">{stream.message}</p>
			<button
				type="button"
				onClick={onRetry}
				className="border border-fg/70 px-3 py-1.5 font-mono text-xs uppercase tracking-[0.2em] hover:bg-fg hover:text-screen focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
			>
				Try again
			</button>
		</div>;
	} else if (stream.status === 'tuning' || starting) {
		content = <div className="grid justify-items-center gap-[2cqw]">
			{channel && <p className="font-mono text-[clamp(0.6rem,3cqw,1.5rem)] uppercase tracking-[0.2em]">{channel.name}</p>}
			<TuningBar/>
		</div>;
	} else if (stalled) {
		// The picture is frozen, so only the bar shows on top of it
		content = <TuningBar/>;
	} else {
		return null;
	}

	return <div className="pointer-events-none absolute inset-0 grid place-items-center p-[4cqw] text-center [&_button]:pointer-events-auto">
		{content}
	</div>;
}

/**
 * A bar that sweeps from left to right while the screen waits for the channel.
 */
function TuningBar() {
	return <div role="status" className="bg-screen/60 p-px">
		<div className="aspect-[10/1] w-[clamp(1.5rem,9cqw,3.5rem)] animate-tuning bg-fg motion-reduce:animate-pulse"/>
		<span className="sr-only">Tuning in</span>
	</div>;
}

function ControlButton({label, pressed, onClick, children}: {label: string, pressed?: boolean, onClick: () => void, children: ReactNode}) {
	return <button
		type="button"
		aria-label={label}
		aria-pressed={pressed}
		title={label}
		onClick={onClick}
		className="grid size-9 shrink-0 place-items-center text-fg hover:bg-fg/15 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
	>
		{children}
	</button>;
}

/**
 * Starts a stream of the channel on the backend, and stops it when the channel or quality changes or the screen goes
 * away.
 *
 * @return the stream, a function to start it again, and a function to give up on it
 */
function useStream(channelId: string | null, quality: string): [StreamState, () => void, (message: string) => void] {
	const [state, setState] = useState<StreamState>({status: 'off'});
	const [attempt, setAttempt] = useState(0);

	useEffect(() => {
		if (!channelId) {
			setState({status: 'off'});
			return;
		}

		let cancelled = false;
		let started: StreamInfo | null = null;
		setState({status: 'tuning'});
		api<StreamInfo>('/api/streams', {method: 'POST', json: {channelId, quality, videoCodecs: playableVideoCodecs()}})
			.then((stream) => {
				if (cancelled) {
					// The channel changed while this one was starting
					stopStream(stream.id);
					return;
				}
				started = stream;
				setState({status: 'playing', stream});
			})
			.catch((error: unknown) => {
				if (!cancelled) {
					setState({status: 'failed', message: error instanceof ApiError ? error.message : 'Kijkbuis can\'t be reached right now'});
				}
			});

		// Stop the stream when the page closes, rather than leaving it to the backend to notice
		const onPageHide = () => started && stopStream(started.id, true);
		window.addEventListener('pagehide', onPageHide);
		return () => {
			cancelled = true;
			window.removeEventListener('pagehide', onPageHide);
			if (started) {
				stopStream(started.id);
			}
		};
	}, [channelId, quality, attempt]);

	const restart = useCallback(() => setAttempt((current) => current + 1), []);
	const fail = useCallback((message: string) => setState({status: 'failed', message}), []);
	return [state, restart, fail];
}

/**
 * @return whether a key press is meant for a text field, rather than a shortcut
 */
function isTyping(target: EventTarget | null): boolean {
	return target instanceof HTMLElement && (target.isContentEditable || target.matches('input:not([type=range]), textarea, select'));
}

/**
 * @return whether the value has been true for the given time. It turns false right away.
 */
function useDelayedTrue(value: boolean, delay: number): boolean {
	const [delayed, setDelayed] = useState(false);

	useEffect(() => {
		if (!value) {
			setDelayed(false);
			return;
		}
		const timeout = window.setTimeout(() => setDelayed(true), delay);
		return () => window.clearTimeout(timeout);
	}, [value, delay]);

	return value && delayed;
}

function stopStream(id: string, keepalive: boolean = false) {
	fetch(`/api/streams/${id}`, {method: 'DELETE', keepalive}).catch(() => {
		// The backend stops streams nobody watches anyway
	});
}

/**
 * Loads a channel's details, and loads them again when the programme changes.
 */
export function useChannel(channelId: string | null): Channel | null {
	const [channel, setChannel] = useState<Channel | null>(null);
	const [refresh, setRefresh] = useState(0);

	useEffect(() => {
		if (!channelId) {
			setChannel(null);
			return;
		}
		let cancelled = false;
		api<Channel>(`/api/channels/${channelId}`)
			.then((loaded) => !cancelled && setChannel(loaded))
			.catch(() => {
				// The screen just doesn't show the channel's name
			});
		return () => {
			cancelled = true;
		};
	}, [channelId, refresh]);

	useEffect(() => {
		// Reload when the programme ends, or when the next one starts if nothing is on, or every few minutes if neither is
		// known
		const changesAt = channel?.now?.endsAt ?? channel?.next?.startsAt;
		const until = changesAt ? Date.parse(changesAt) - Date.now() + 5_000 : 5 * 60_000;
		const timeout = window.setTimeout(() => setRefresh((current) => current + 1), Math.max(until, 5_000));
		return () => window.clearTimeout(timeout);
	}, [channel]);

	return channel?.id === channelId ? channel : null;
}
