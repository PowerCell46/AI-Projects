import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { useProfilePictureSource } from '../../../../../hooks/useProfilePictureSource';
import { ROUTES } from '../../../../../routes';
import { SIGNAL_LOST_MESSAGE } from '../../../../../utils/authErrors';
import { useLogout } from './useLogout';
import './UserMenu.css';


type FirstFocus = 'first' | 'last';

function menuItemsOf(menu: HTMLElement | null): HTMLElement[] {
    return Array.from(menu?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
}

function indexAfterStep(currentIndex: number, itemCount: number, step: number): number {
    return (currentIndex + step + itemCount) % itemCount;
}

interface UserMenuProps {
    pictureUrl: string | null;
}

function UserMenu({ pictureUrl }: UserMenuProps) {
    const { isLoggingOut, hasFailed, logOut } = useLogout();
    const picture = useProfilePictureSource(pictureUrl);
    const [isOpen, setIsOpen] = useState(false);
    const menuId = useId();
    const containerRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const firstFocusRef = useRef<FirstFocus>('first');

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const items = menuItemsOf(menuRef.current);

        items.at(firstFocusRef.current === 'first' ? 0 : -1)?.focus();
    }, [isOpen]);

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        function handlePointerDown(event: PointerEvent) {
            if (event.target instanceof Node && !containerRef.current?.contains(event.target)) {
                setIsOpen(false);
            }
        }

        document.addEventListener('pointerdown', handlePointerDown);

        return () => document.removeEventListener('pointerdown', handlePointerDown);
    }, [isOpen]);

    function openMenu(firstFocus: FirstFocus) {
        firstFocusRef.current = firstFocus;
        setIsOpen(true);
    }

    function closeMenuAndFocusTrigger() {
        setIsOpen(false);
        triggerRef.current?.focus();
    }

    function handleTriggerClick() {
        if (isOpen) {
            setIsOpen(false);

        } else {
            openMenu('first');
        }
    }

    function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            openMenu(event.key === 'ArrowDown' ? 'first' : 'last');
        }
    }

    function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        const items = menuItemsOf(menuRef.current);
        const currentIndex = items.findIndex((item) => item === document.activeElement);

        if (event.key === 'Escape' || event.key === 'Tab') {
            closeMenuAndFocusTrigger();

        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            items[indexAfterStep(currentIndex, items.length, event.key === 'ArrowDown' ? 1 : -1)]?.focus();

        } else if (event.key === 'Home' || event.key === 'End') {
            event.preventDefault();
            items.at(event.key === 'Home' ? 0 : -1)?.focus();
        }
    }

    return (
        <div className="user-menu" ref={containerRef}>
            <button
                type="button"
                className="user-menu-trigger"
                ref={triggerRef}
                aria-label="Account menu"
                aria-haspopup="menu"
                aria-expanded={isOpen}
                aria-controls={isOpen ? menuId : undefined}
                data-open={isOpen}
                onClick={handleTriggerClick}
                onKeyDown={handleTriggerKeyDown}
            >
                <span className="user-menu-avatar">
                    <img className="user-menu-picture" src={picture.src} onError={picture.onError} alt="" />
                </span>
            </button>
            {isOpen && (
                <div className="user-menu-panel">
                    <div
                        className="user-menu-list"
                        id={menuId}
                        role="menu"
                        aria-label="Account"
                        ref={menuRef}
                        onKeyDown={handleMenuKeyDown}
                    >
                        <Link
                            className="user-menu-item"
                            to={ROUTES.saved}
                            role="menuitem"
                            tabIndex={-1}
                            onClick={closeMenuAndFocusTrigger}
                        >
                            SAVED TWEETS
                        </Link>
                        <Link
                            className="user-menu-item"
                            to={ROUTES.liked}
                            role="menuitem"
                            tabIndex={-1}
                            onClick={closeMenuAndFocusTrigger}
                        >
                            LIKED TWEETS
                        </Link>
                        <button
                            type="button"
                            className="user-menu-item"
                            data-tone="destructive"
                            role="menuitem"
                            tabIndex={-1}
                            aria-disabled={isLoggingOut}
                            onClick={logOut}
                        >
                            LOG OUT
                        </button>
                    </div>
                    <p className="user-menu-error" role="alert">{hasFailed ? SIGNAL_LOST_MESSAGE : ''}</p>
                </div>
            )}
        </div>
    );
}

export default UserMenu;
