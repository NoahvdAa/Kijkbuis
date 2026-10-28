import {createContext, type ReactNode, useCallback, useContext, useMemo, useState} from 'react';
import {singleAudioSource} from './media';
import {AUTO_QUALITY} from './profiles';
import {type Grid, maxGrid, useLandscape} from './tvLayout';

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

type TvState = {
	/** The screens, from left to right and top to bottom */
	tiles: Tile[]
	/** The multiview grid */
	grid: Grid
	/** The largest grid that can be picked on this screen */
	maxGrid: Grid
	/** The screen that picking a channel puts it on */
	activeTileId: string
	/** Switches multiview to a grid. Screens that are still in the grid keep their place, and what they show. */
	setGrid: (grid: Grid) => void
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

let lastTileId = 0;

function newTile(): Tile {
	// Not crypto.randomUUID(), which only exists on HTTPS and localhost
	return {id: String(++lastTileId), channelId: null, muted: false, volume: 1, quality: AUTO_QUALITY};
}

type Screens = {
	tiles: Tile[]
	activeTileId: string
	grid: Grid
};

/**
 * Holds what the TV shows. It lives above the pages, so menus can change it.
 */
export function TvProvider({children}: {children: ReactNode}) {
	const landscape = useLandscape();
	const [screens, setScreens] = useState<Screens>(() => {
		const tile = newTile();
		return {tiles: [tile], activeTileId: tile.id, grid: {columns: 1, rows: 1}};
	});

	const updateTiles = useCallback((update: (tiles: Tile[], activeTileId: string) => Tile[]) => {
		setScreens((current) => ({...current, tiles: update(current.tiles, current.activeTileId)}));
	}, []);

	const setGrid = useCallback((grid: Grid) => setScreens((current) => {
		// Screens keep their place in the grid, like when resizing a table
		const tiles: Tile[] = [];
		for (let row = 0; row < grid.rows; row++) {
			for (let column = 0; column < grid.columns; column++) {
				const existing = row < current.grid.rows && column < current.grid.columns
					? current.tiles[row * current.grid.columns + column]
					: undefined;
				tiles.push(existing ?? newTile());
			}
		}
		return {
			tiles,
			activeTileId: tiles.some((tile) => tile.id === current.activeTileId) ? current.activeTileId : tiles[0].id,
			grid,
		};
	}), []);

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
		maxGrid: maxGrid(landscape),
		setGrid,
		setActiveTile,
		watch,
		stop,
		setMuted,
		setVolume,
		setQuality,
	}), [screens, landscape, setGrid, setActiveTile, watch, stop, setMuted, setVolume, setQuality]);
	return <TvContext value={state}>{children}</TvContext>;
}

export function useTv(): TvState {
	const state = useContext(TvContext);
	if (!state) {
		throw new Error('useTv must be used inside a TvProvider');
	}
	return state;
}
