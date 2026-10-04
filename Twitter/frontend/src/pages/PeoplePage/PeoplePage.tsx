import { useShellContext } from '../../components/shared/Shell/useShellContext';
import PeopleList from './PeopleList/PeopleList';


function PeoplePage() {
    const { onFollowChanged } = useShellContext();

    return (
        <>
            <h1 className="sr-only">People</h1>
            <PeopleList onFollowChanged={onFollowChanged} />
        </>
    );
}

export default PeoplePage;
