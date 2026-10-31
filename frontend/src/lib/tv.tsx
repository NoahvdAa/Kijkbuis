import {createContext, type ReactNode, useCallback, useContext, useMemo, useState} from 'react';
import {singleAudioSource} from './media';
import {AUTO_QUALITY} from './profiles';
import {clampFrame, type Frame, type Grid, gridAspect, MAX_SCREENS, maxGrid, newFrame, TILE_ASPECT, useLandscape} from './tvLayout';

/**
 * One screen of the TV. With multiview, there are several.
 */
export type Tile = {
	id: string
	/** The channel the tile shows, or null if it shows nothing */
	channelId: string | null
	muted: boolean
	/** From 0 to 1 */
	volume: number
	/** {@link AUTO_QUALITY} or the ID of a transcoding profile. It stays when the screen changes channels. */
	quality: string
};

/**
 * How the screens are arranged: in a grid, or wherever the user put them.
 */
export type Layout =
	| {kind: 'grid', grid: Grid}
	/** Each screen's frame by its ID, and the order they're drawn in, from the bottom up */
	| {kind: 'custom', frames: Record<string, Frame>, stack: string[]};

/**
 * A custom layout the user arranged, to come back to. The screens are in the order of their numbers.
 */
export type SavedLayout = {
	frames: Frame[]
	/** The screens' indexes in the order they're drawn in, from the bottom up */
	stack: number[]
};

type TvState = {
	/** The screens. In a grid they go from left to right and top to bottom, and their order is their number. */
	tiles: Tile[]
	layout: Layout
	/** The shape of the TV's picture, as its width divided by its height */
	aspect: number
	/** The largest grid that can be picked on this screen */
	maxGrid: Grid
	/** The custom layout last arranged in this browser, if any */
	savedLayout: SavedLayout | null
	/** The screen that picking a channel puts it on */
	activeTileId: string
	/** Switches multiview to a grid. Screens that are still in the grid keep their place, and what they show. */
	setGrid: (grid: Grid) => void
	/**
	 * Switches to a custom layout. The screens keep what they show, in the order of their numbers, and screens are
	 * added or removed to match. If the selected screen shows something, the first added one is selected instead.
	 *
	 * @param stack the order the screens are drawn in, from the bottom up, as their indexes. In the order of their
	 *              numbers if left out.
	 */
	setFrames: (frames: Frame[], stack?: number[]) => void
	/** Moves or resizes a screen of a custom layout */
	setFrame: (tileId: string, frame: Frame) => void
	/** Puts a screen of a custom layout in a layer, where 0 is behind all others, and the others keep their order */
	setLayer: (tileId: string, layer: number) => void
	/** Adds a screen to a custom layout, which becomes the one picking a channel puts it on */
	addScreen: () => void
	/** Removes a screen from a custom layout, unless it's the last one */
	removeScreen: (tileId: string) => void
	/** Remembers the custom layout in this browser, to come back to later */
	saveLayout: () => void
	setActiveTile: (tileId: string) => void
	/** Shows a channel on the active screen. It starts muted if another screen already has sound. */
	watch: (channelId: string) => void
	/** Stops what a screen shows */
	stop: (tileId: string) => void
	/** Mutes or unmutes a screen. On phones and tablets, unmuting one mutes the others. */
	setMuted: (tileId: string, muted: boolean) => void
	setVolume: (tileId: string, volume: number) => void
	setQuality: (tileId: string, quality: string) => void
};

const TvContext = createContext<TvState | null>(null);

/** Where the custom layout is remembered */
const SAVED_LAYOUT_KEY = 'kijkbuis.customLayout';

let lastTileId = 0;

function newTile(): Tile {
	// Not crypto.randomUUID(), which only exists on HTTPS and localhost
	return {id: String(++lastTileId), channelId: null, muted: false, volume: 1, quality: AUTO_QUALITY};
}

type Screens = {
	tiles: Tile[]
	activeTileId: string
	layout: Layout
};

/**
 * Holds what the TV shows. It lives above the pages, so menus can change it.
 */
export function TvProvider({children}: {children: ReactNode}) {
	const landscape = useLandscape();
	const [screens, setScreens] = useState<Screens>(() => {
		const tile = newTile();
		return {tiles: [tile], activeTileId: tile.id, layout: {kind: 'grid', grid: {columns: 1, rows: 1}}};
	});
	const [savedLayout, setSavedLayout] = useState(loadLayout);

	const updateTiles = useCallback((update: (tiles: Tile[], activeTileId: string) => Tile[]) => {
		setScreens((current) => ({...current, tiles: update(current.tiles, current.activeTileId)}));
	}, []);

	const setGrid = useCallback((grid: Grid) => setScreens((current) => {
		const tiles: Tile[] = [];
		for (let row = 0; row < grid.rows; row++) {
			for (let column = 0; column < grid.columns; column++) {
				let existing: Tile | undefined;
				if (current.layout.kind === 'grid') {
					// Screens keep their place in the grid, like when resizing a table
					const previous = current.layout.grid;
					existing = row < previous.rows && column < previous.columns ? current.tiles[row * previous.columns + column] : undefined;
				} else {
					// Custom layouts have no rows and columns, so the screens go in the order of their numbers
					existing = current.tiles[row * grid.columns + column];
				}
				tiles.push(existing ?? newTile());
			}
		}
		return {tiles, activeTileId: keepActive(tiles, current.activeTileId), layout: {kind: 'grid', grid}};
	}), []);

	const setFrames = useCallback((frames: Frame[], stack?: number[]) => setScreens((current) => {
		const tiles = frames.map((_, index) => current.tiles[index] ?? newTile());
		const order = stack && stack.length === frames.length ? stack : frames.map((_, index) => index);
		// Picking a channel next fills the first new screen, such as the small one of a picture in picture, unless the
		// selected screen is still empty
		const selected = tiles.find((tile) => tile.id === current.activeTileId);
		const added = tiles.find((tile) => !current.tiles.includes(tile));
		return {
			tiles,
			activeTileId: selected && (!selected.channelId || !added) ? selected.id : (added ?? tiles[0]).id,
			layout: {
				kind: 'custom',
				frames: Object.fromEntries(tiles.map((tile, index) => [tile.id, frames[index]])),
				stack: order.map((index) => tiles[index].id),
			},
		};
	}), []);

	const setFrame = useCallback((tileId: string, frame: Frame) => setScreens((current) => current.layout.kind === 'custom'
		? {...current, layout: {...current.layout, frames: {...current.layout.frames, [tileId]: frame}}}
		: current), []);

	const setLayer = useCallback((tileId: string, layer: number) => setScreens((current) => {
		if (current.layout.kind !== 'custom' || !current.layout.stack.includes(tileId)) {
			return current;
		}
		const stack = current.layout.stack.filter((id) => id !== tileId);
		stack.splice(Math.max(0, Math.min(layer, stack.length)), 0, tileId);
		return {...current, layout: {...current.layout, stack}};
	}), []);

	const addScreen = useCallback(() => setScreens((current) => {
		if (current.layout.kind !== 'custom' || current.tiles.length >= MAX_SCREENS) {
			return current;
		}
		const tile = newTile();
		return {
			tiles: [...current.tiles, tile],
			activeTileId: tile.id,
			layout: {
				kind: 'custom',
				frames: {...current.layout.frames, [tile.id]: newFrame(Object.values(current.layout.frames))},
				stack: [...current.layout.stack, tile.id],
			},
		};
	}), []);

	const removeScreen = useCallback((tileId: string) => setScreens((current) => {
		if (current.layout.kind !== 'custom' || current.tiles.length <= 1) {
			return current;
		}
		const tiles = current.tiles.filter((tile) => tile.id !== tileId);
		const stack = current.layout.stack.filter((id) => id !== tileId);
		const frames = {...current.layout.frames};
		delete frames[tileId];
		return {
			tiles,
			// The one on top takes over, if it was the removed one
			activeTileId: current.activeTileId === tileId ? stack.at(-1)! : current.activeTileId,
			layout: {kind: 'custom', frames, stack},
		};
	}), []);

	const saveLayout = useCallback(() => {
		const {tiles, layout} = screens;
		if (layout.kind !== 'custom') {
			return;
		}
		const saved: SavedLayout = {
			frames: tiles.map((tile) => layout.frames[tile.id]),
			stack: layout.stack.map((id) => tiles.findIndex((tile) => tile.id === id)),
		};
		setSavedLayout(saved);
		try {
			localStorage.setItem(SAVED_LAYOUT_KEY, JSON.stringify(saved));
		} catch {
			// Only remembered until the page reloads then
		}
	}, [screens]);

	const setActiveTile = useCallback((tileId: string) => setScreens((current) => ({...current, activeTileId: tileId})), []);

	const watch = useCallback((channelId: string) => updateTiles((tiles, activeTileId) => {
		const othersHaveSound = tiles.some((tile) => tile.id !== activeTileId && tile.channelId && !tile.muted);
		return tiles.map((tile) => tile.id === activeTileId ? {...tile, channelId, muted: othersHaveSound || tile.muted} : tile);
	}), [updateTiles]);

	const stop = useCallback((tileId: string) => updateTiles((tiles) => tiles.map(
		(tile) => tile.id === tileId ? {...tile, channelId: null, muted: false} : tile
	)), [updateTiles]);

	const setMuted = useCallback((tileId: string, muted: boolean) => updateTiles((tiles) => tiles.map((tile) => {
		if (tile.id === tileId) {
			// Unmuting at zero volume would still be silent
			return {...tile, muted, volume: !muted && tile.volume === 0 ? 1 : tile.volume};
		}
		// Phones and tablets only play one video with sound at a time
		return !muted && singleAudioSource ? {...tile, muted: true} : tile;
	})), [updateTiles]);

	const setVolume = useCallback((tileId: string, volume: number) => updateTiles((tiles) => tiles.map(
		(tile) => tile.id === tileId ? {...tile, volume, muted: volume === 0} : tile
	)), [updateTiles]);

	const setQuality = useCallback((tileId: string, quality: string) => updateTiles((tiles) => tiles.map(
		(tile) => tile.id === tileId ? {...tile, quality} : tile
	)), [updateTiles]);

	const state = useMemo(() => ({
		...screens,
		aspect: screens.layout.kind === 'grid' ? gridAspect(screens.layout.grid) : TILE_ASPECT,
		maxGrid: maxGrid(landscape),
		savedLayout,
		setGrid,
		setFrames,
		setFrame,
		setLayer,
		addScreen,
		removeScreen,
		saveLayout,
		setActiveTile,
		watch,
		stop,
		setMuted,
		setVolume,
		setQuality,
	}), [screens, landscape, savedLayout, setGrid, setFrames, setFrame, setLayer, addScreen, removeScreen, saveLayout, setActiveTile, watch, stop, setMuted,
		setVolume, setQuality]);
	return <TvContext value={state}>{children}</TvContext>;
}

export function useTv(): TvState {
	const state = useContext(TvContext);
	if (!state) {
		throw new Error('useTv must be used inside a TvProvider');
	}
	return state;
}

/**
 * @return whether the TV has a custom layout with these frames, drawn in this order
 *
 * @param stack the order the screens are drawn in, from the bottom up, as their indexes. In the order of their numbers
 *              if left out.
 */
export function hasFrames(layout: Layout, tiles: Tile[], frames: Frame[], stack?: number[]): boolean {
	if (layout.kind !== 'custom' || tiles.length !== frames.length) {
		return false;
	}
	const order = stack ?? frames.map((_, index) => index);
	const close = (a: number, b: number) => Math.abs(a - b) < 1e-6;
	return tiles.every((tile, index) => {
		const frame = layout.frames[tile.id];
		return close(frame.x, frames[index].x) && close(frame.y, frames[index].y) && close(frame.size, frames[index].size);
	}) && layout.stack.every((id, layer) => tiles[order[layer]]?.id === id);
}

/**
 * @return the active screen if it's still there, or else the first one
 */
function keepActive(tiles: Tile[], activeTileId: string): string {
	return tiles.some((tile) => tile.id === activeTileId) ? activeTileId : tiles[0].id;
}

/**
 * @return the custom layout remembered in this browser, if there is one that still makes sense
 */
function loadLayout(): SavedLayout | null {
	try {
		const saved = JSON.parse(localStorage.getItem(SAVED_LAYOUT_KEY) ?? 'null') as SavedLayout | null;
		const valid = saved !== null && Array.isArray(saved.frames) && saved.frames.length >= 1 && saved.frames.length <= MAX_SCREENS
			&& saved.frames.every((frame) => [frame?.x, frame?.y, frame?.size].every((value) => typeof value === 'number' && Number.isFinite(value)))
			&& Array.isArray(saved.stack) && saved.stack.length === saved.frames.length
			&& [...saved.stack].sort((a, b) => a - b).every((index, position) => index === position);
		// Made to fit, in case it was saved by a version of Kijkbuis with other limits
		return valid ? {frames: saved.frames.map(clampFrame), stack: saved.stack} : null;
	} catch {
		return null;
	}
}
