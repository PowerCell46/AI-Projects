import type { Ref } from 'react';
import './PostButton.css';


interface PostButtonProps {
    ref?: Ref<HTMLButtonElement>;
    onClick: () => void;
}

function PostButton({ ref, onClick }: PostButtonProps) {
    return (
        <button type="button" className="post-button" aria-label="Post" ref={ref} onClick={onClick}>
            <span className="post-button-plus" aria-hidden="true" />
            <span className="post-button-label" aria-hidden="true">POST</span>
        </button>
    );
}

export default PostButton;
