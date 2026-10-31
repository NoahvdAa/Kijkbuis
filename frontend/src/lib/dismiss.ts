import {type RefObject, useEffect} from 'react';

/**
 * Closes a popup when the user clicks outside it or presses Escape.
 *
 * @param open       whether the popup is open
 * @param container  the element holding the popup and the button that opens it
 * @param onDismiss  closes the popup
 * @param keepOpenOn a selector for elements outside the popup that change what it shows rather than close it, so it
 *                   doesn't close and open again (which flashes)
 */
export function useDismiss(open: boolean, container: RefObject<HTMLElement | null>, onDismiss: () => void, keepOpenOn?: string): void {
    useEffect(() => {
        if (!open) {
            return;
        }
        const onPointerDown = (event: PointerEvent) => {
            const target = event.target as Node;
            const keepOpen = keepOpenOn !== undefined && target instanceof Element && target.closest(keepOpenOn) !== null;
            if (!container.current?.contains(target) && !keepOpen) {
                onDismiss();
            }
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                onDismiss();
            }
        };
        // Captured, so closing this goes before anything else that clicks or Escape do
        document.addEventListener('pointerdown', onPointerDown, true);
        window.addEventListener('keydown', onKeyDown, true);
        return () => {
            document.removeEventListener('pointerdown', onPointerDown, true);
            window.removeEventListener('keydown', onKeyDown, true);
        };
    }, [open, container, onDismiss, keepOpenOn]);
}
