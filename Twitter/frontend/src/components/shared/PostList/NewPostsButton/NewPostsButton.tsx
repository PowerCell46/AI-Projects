import './NewPostsButton.css';


interface NewPostsButtonProps {
    onClick: () => void;
}

function NewPostsButton({ onClick }: NewPostsButtonProps) {
    return (
        <button type="button" className="new-posts-button" onClick={onClick}>NEW POSTS</button>
    );
}

export default NewPostsButton;
