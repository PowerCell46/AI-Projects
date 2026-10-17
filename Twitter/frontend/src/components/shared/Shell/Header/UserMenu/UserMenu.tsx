import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../../../../contexts/AuthContext';
import { useProfilePictureSource } from '../../../../../hooks/social/useProfilePictureSource';
import { ROUTES, profilePath } from '../../../../../routes';
import { wrapIndex } from '../../../../../utils/navigation/wrapIndex';
import { SIGNAL_LOST_MESSAGE } from '../../../../../utils/validation/authErrors';
import { useLogout } from './useLogout';
import './UserMenu.css';


type FirstFocus = 'first' | 'last';

function menuItemsOf(menu: HTMLElement | null): HTMLElement[] {
    return Array.from(menu?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
}

interface MenuLink {
    label: string;
    path: string;
}

interface UserMenuProps {
    pictureUrl: string | null;
}

function UserMenu({ pictureUrl }: UserMenuProps) {
    const { user } = useAuth();
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

            const step = event.key === 'ArrowDown' ? 1 : -1;

            items[wrapIndex(currentIndex, step, items.length)]?.focus();

        } else if (event.key === 'Home' || event.key === 'End') {
            event.preventDefault();
            items.at(event.key === 'Home' ? 0 : -1)?.focus();
        }
    }

    const profileLinks: MenuLink[] = user
        ? [{
            label: 'PROFILE',
            path: profilePath(user.username),
        }]
        : [];
    const menuLinks: MenuLink[] = [
        ...profileLinks,
        {
            label: 'SAVED TWEETS',
            path: ROUTES.saved,
        },
        {
            label: 'LIKED TWEETS',
            path: ROUTES.liked,
        },
    ];

    return (
        <div className="user-menu" ref={containerRef}>
            <button
                type="button"
                className="user-menu-trigger"
                aria-label="Account menu"
                aria-haspopup="menu"
                aria-expanded={isOpen}
                aria-controls={isOpen ? menuId : undefined}
                data-open={isOpen}
                ref={triggerRef}
                onClick={handleTriggerClick}
                onKeyDown={handleTriggerKeyDown}
            >
                <span className="user-menu-avatar">
                    <img
                        className="user-menu-picture"
                        src={picture.src}
                        alt=""
                        onError={picture.onError}
                    />
                </span>
            </button>
            {isOpen && (
                <div className="user-menu-panel">
                    <div
                        id={menuId}
                        className="user-menu-list"
                        aria-label="Account"
                        role="menu"
                        ref={menuRef}
                        onKeyDown={handleMenuKeyDown}
                    >
                        {menuLinks.map((menuLink) => (
                            <Link
                                key={menuLink.path}
                                className="user-menu-item"
                                to={menuLink.path}
                                role="menuitem"
                                tabIndex={-1}
                                onClick={closeMenuAndFocusTrigger}
                            >
                                {menuLink.label}
                            </Link>
                        ))}
                        <button
                            type="button"
                            className="user-menu-item"
                            data-tone="destructive"
                            aria-disabled={isLoggingOut}
                            role="menuitem"
                            tabIndex={-1}
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
