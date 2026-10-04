import type { Ref } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../../routes';
import PostButton from './PostButton/PostButton';
import UserMenu from './UserMenu/UserMenu';
import './Header.css';


interface HeaderProps {
    pictureUrl: string | null;
    postButtonRef: Ref<HTMLButtonElement>;
    onPostClick: () => void;
}

function Header({ pictureUrl, postButtonRef, onPostClick }: HeaderProps) {
    return (
        <header className="header">
            <Link className="header-brand" to={ROUTES.feed}>TWITTER</Link>
            <div className="header-actions">
                <PostButton ref={postButtonRef} onClick={onPostClick} />
                <UserMenu pictureUrl={pictureUrl} />
            </div>
        </header>
    );
}

export default Header;
