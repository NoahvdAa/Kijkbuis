import {Link} from '@tanstack/react-router';
import {type RefObject, useCallback, useEffect, useRef, useState} from 'react';
import {hasPrivilege, type User} from '#/lib/auth';
import {canFullscreenPage, toggleFullscreen, useFullscreenElement} from '#/lib/media';
import {useProfiles} from '#/lib/profiles';
import {type Tile, useTv} from '#/lib/tv';
import {useTvAspect} from '#/lib/tvLayout';
import type {Profile} from '#/lib/types';
import {Brand} from './Brand';
import {ExitFullscreenIcon, FullscreenIcon} from './Icons';
import {MultiviewPicker} from './MultiviewPicker';
import {Player} from './Player';

type TvScreenProps = {
	/** Full screen while watching, or small in the top left while a menu is open */
	mode: 'full' | 'pip'
	user: User
};

/** How long the controls stay visible after the mouse last moved */
const CONTROLS_TIMEOUT = 3_000;
/** The menu that's open when picking the multiview grid. The screens' quality menus are named after their tile. */
const MULTIVIEW_MENU = 'multiview';

/**
 * The TV. It's always on screen, and it's the same element whether it's full screen or in the corner, so moving
 * between the two never interrupts what's playing. With multiview it shows a grid of screens, arranged to suit the
 * shape of the device, and the corner version shows the same grid.
 */
export function TvScreen({mode, user}: TvScreenProps) {
	const {tiles, grid, activeTileId, setActiveTile} = useTv();
	useTvAspect(grid);
	const section = useRef<HTMLElement>(null);
	const [awake, wake] = useAwake(section);
	// A new channel brings up the controls, with its info banner
	const channels = tiles.map((tile) => tile.channelId).join();
	useEffect(() => {
		wake();
	}, [channels, wake]);
	const profiles = useProfiles(hasPrivilege(user, 'transcode'));
	const pip = mode === 'pip';
	const multiview = tiles.length > 1;

	// One menu is open at a time, and the controls stay visible while it is
	const [openMenu, setOpenMenu] = useState<string | null>(null);
	const setMenuOpen = useCallback((menu: string, open: boolean) => {
		setOpenMenu((current) => open ? menu : current === menu ? null : current);
	}, []);
	const setMultiviewOpen = useCallback((open: boolean) => setMenuOpen(MULTIVIEW_MENU, open), [setMenuOpen]);
	useEffect(() => {
		// The corner version has no controls, so menus shouldn't pop up again when coming back
		if (pip) {
			setOpenMenu(null);
		}
	}, [pip]);

	// While nothing is playing, there's nothing to hide the controls for
	const showControls = !pip && (awake || openMenu !== null || !tiles.some((tile) => tile.channelId));

	return <section
		ref={section}
		aria-label="TV"
		onPointerMove={wake}
		onPointerDown={wake}
		onKeyDown={wake}
		className={`tv fixed z-20 overflow-hidden bg-screen transition-[top,left,width,height] duration-300 ease-out motion-reduce:transition-none ${pip ? 'tv-pip shadow-2xl ring-1 ring-line' : 'tv-full'} ${!pip && !showControls ? 'cursor-none' : ''}`}
	>
		<div className={`tv-grid absolute inset-0 m-auto flex flex-wrap content-center justify-center ${multiview ? 'bg-line' : ''}`}>
			{tiles.map((tile, index) => <TileView
				key={tile.id}
				tile={tile}
				number={index + 1}
				width={100 / grid.columns}
				multiview={multiview}
				pip={pip}
				active={tile.id === activeTileId}
				awake={showControls}
				profiles={profiles}
				menuOpen={openMenu === tile.id}
				onMenuOpenChange={setMenuOpen}
				onSelect={setActiveTile}
				onWake={wake}
			/>)}
		</div>

		{pip
			? <Link to="/" aria-label="Back to the TV" className="absolute inset-0 z-20 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"/>
			: <TvOverlay user={user} visible={showControls} pickingMultiview={openMenu === MULTIVIEW_MENU} onPickingMultiviewChange={setMultiviewOpen}/>}
	</section>;
}

type TileViewProps = {
	tile: Tile
	number: number
	/** The share of the TV's width the tile takes, in percent */
	width: number
	multiview: boolean
	pip: boolean
	/** Whether this is the screen picking a channel puts it on */
	active: boolean
	/** Whether the controls are showing */
	awake: boolean
	profiles: Profile[]
	/** Whether the screen's quality menu is open */
	menuOpen: boolean
	onMenuOpenChange: (tileId: string, open: boolean) => void
	onSelect: (tileId: string) => void
	onWake: () => void
};

function TileView({tile, number, width, multiview, pip, active, awake, profiles, menuOpen, onMenuOpenChange, onSelect, onWake}: TileViewProps) {
	const [hovered, setHovered] = useState(false);
	const select = useCallback(() => onSelect(tile.id), [onSelect, tile.id]);
	const setMenuOpen = useCallback((open: boolean) => onMenuOpenChange(tile.id, open), [onMenuOpenChange, tile.id]);

	return <div
		className={`relative aspect-video ${multiview ? 'p-px' : ''}`}
		style={{width: `${width}%`}}
		onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(true)}
		onPointerLeave={() => setHovered(false)}
	>
		<div className="relative h-full">
			<Player
				tile={tile}
				number={number}
				multiview={multiview}
				pip={pip}
				// In multiview, only the screen under the mouse and the selected one show their controls
				showControls={awake && (!multiview || hovered || active || menuOpen)}
				tvAwake={awake}
				active={active}
				profiles={profiles}
				menuOpen={menuOpen}
				onMenuOpenChange={setMenuOpen}
				onSelect={select}
				onWake={onWake}
			/>
			{/*
				Marks the screen that picking a channel puts it on. It's drawn on top, so the picture can't cover it. The
				corner TV always shows it, since that's where the menus pick channels for it. The full TV only shows it with
				the controls, so it doesn't sit around one screen while just watching.
			*/}
			{multiview && active && <span
				aria-hidden="true"
				className={`pointer-events-none absolute inset-0 z-[15] ring-2 ring-inset ring-accent transition-opacity duration-200 ${pip || awake ? 'opacity-100' : 'opacity-0'}`}
			/>}
		</div>
	</div>;
}

const overlayButtonClassName = 'border border-fg/70 px-3 py-2 font-mono text-xs uppercase tracking-[0.2em] text-fg transition-colors '
	+ 'hover:bg-fg hover:text-screen focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';

type TvOverlayProps = {
	user: User
	visible: boolean
	pickingMultiview: boolean
	onPickingMultiviewChange: (open: boolean) => void
};

function TvOverlay({user, visible, pickingMultiview, onPickingMultiviewChange}: TvOverlayProps) {
	const fullscreen = useFullscreenElement() === document.documentElement;

	return <header
		className={`absolute inset-x-0 top-0 z-20 flex flex-wrap items-center gap-x-6 gap-y-3 bg-screen/80 px-4 py-3 transition-opacity duration-200 sm:px-6 ${visible ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
		inert={!visible}
	>
		<Brand/>

		<MultiviewPicker className={overlayButtonClassName} open={pickingMultiview} onOpenChange={onPickingMultiviewChange}/>

		<nav className="flex flex-wrap gap-2 sm:ml-auto">
			<Link to="/channels" className={overlayButtonClassName}>Channels</Link>
			<Link to="/guide" className={overlayButtonClassName}>Guide</Link>
			{hasPrivilege(user, 'admin') && <Link to="/admin" className={overlayButtonClassName}>Admin</Link>}
			<Link to="/account" className={overlayButtonClassName}>{user.displayName}</Link>
			{canFullscreenPage() && <button
				type="button"
				aria-label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
				title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
				onClick={() => toggleFullscreen(document.documentElement)}
				// Less padding than the text buttons, so the icon makes it just as tall
				className={overlayButtonClassName.replace('px-3 py-2', 'p-1.5')}
			>
				{fullscreen ? <ExitFullscreenIcon/> : <FullscreenIcon/>}
			</button>}
		</nav>
	</header>;
}

/**
 * Tracks whether the user is doing something on the TV: moving the mouse, touching the screen or using the keyboard.
 * After a few seconds of nothing, the TV is no longer awake and hides its controls. It stays awake while the keyboard
 * focus is on one of its controls, so they don't disappear from under keyboard users.
 *
 * @return whether the TV is awake, and a function that wakes it up
 */
function useAwake(element: RefObject<HTMLElement | null>): [boolean, () => void] {
	const [awake, setAwake] = useState(true);
	const timeout = useRef<number | undefined>(undefined);

	const wake = useCallback(() => {
		setAwake(true);
		window.clearTimeout(timeout.current);
		const sleep = () => {
			const focused = document.activeElement;
			if (focused instanceof HTMLElement && element.current?.contains(focused) && focused.matches(':focus-visible')) {
				timeout.current = window.setTimeout(sleep, CONTROLS_TIMEOUT);
			} else {
				setAwake(false);
			}
		};
		timeout.current = window.setTimeout(sleep, CONTROLS_TIMEOUT);
	}, [element]);

	useEffect(() => {
		wake();
		return () => window.clearTimeout(timeout.current);
	}, [wake]);

	return [awake, wake];
}
