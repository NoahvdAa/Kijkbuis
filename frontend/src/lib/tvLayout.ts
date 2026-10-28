import {useEffect, useState} from 'react';

/** Every tile shows a 16:9 picture */
export const TILE_ASPECT = 16 / 9;

/**
 * A multiview grid of screens.
 */
export type Grid = {
	columns: number
	rows: number
};

/**
 * @return the largest grid multiview offers: wider on landscape screens, taller on portrait ones
 */
export function maxGrid(landscape: boolean): Grid {
	return landscape ? {columns: 3, rows: 2} : {columns: 2, rows: 3};
}

/**
 * @return whether the window is wider than it's tall, updated when it's resized or the device is rotated
 */
export function useLandscape(): boolean {
	const [landscape, setLandscape] = useState(() => window.innerWidth >= window.innerHeight);

	useEffect(() => {
		const onResize = () => setLandscape(window.innerWidth >= window.innerHeight);
		window.addEventListener('resize', onResize);
		return () => window.removeEventListener('resize', onResize);
	}, []);

	return landscape;
}

/**
 * Publishes the shape of the TV's picture as the --tv-aspect CSS variable, which the PiP and the menus' headers are
 * sized by.
 */
export function useTvAspect(grid: Grid): void {
	const aspect = grid.columns * TILE_ASPECT / grid.rows;

	useEffect(() => {
		document.documentElement.style.setProperty('--tv-aspect', String(aspect));
		return () => {
			document.documentElement.style.removeProperty('--tv-aspect');
		};
	}, [aspect]);
}
