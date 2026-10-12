import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/http';
import { STATUS_NOT_FOUND } from '../api/httpStatus';


export type DetailsProblem = 'not-found' | 'failed';

export interface DetailsLoader<T> {
    loaded: T | null;
    problem: DetailsProblem | null;
    retry: () => void;
    replaceLoaded: (replacement: T) => void;
}

// One fetch with a 404/failed split and a retry, for the pages that show one thing: a null key makes no request,
// and neither a value nor a problem means it is still loading. A `404` is a thing that is gone; any other failure
// can be tried again.
export function useDetailsLoader<T>(key: string | null, load: (key: string) => Promise<T>): DetailsLoader<T> {
    const [loaded, setLoaded] = useState<T | null>(null);
    const [problem, setProblem] = useState<DetailsProblem | null>(null);
    const [attempt, setAttempt] = useState(0);
    const loadRef = useRef(load);

    useEffect(() => {
        loadRef.current = load;
    });

    useEffect(() => {
        if (key === null) {
            return;
        }

        let isCancelled = false;

        loadRef.current(key)
            .then((value) => {
                if (isCancelled) {
                    return;
                }

                setLoaded(value);
            })
            .catch((failure: unknown) => {
                if (isCancelled) {
                    return;
                }

                setProblem(failure instanceof ApiError && failure.status === STATUS_NOT_FOUND ? 'not-found' : 'failed');
            });

        return () => {
            isCancelled = true;
        };
    }, [key, attempt]);

    function retry() {
        setProblem(null);
        setAttempt((currentAttempt) => currentAttempt + 1);
    }

    function replaceLoaded(replacement: T) {
        setLoaded(replacement);
    }

    return {
        loaded,
        problem,
        retry,
        replaceLoaded,
    };
}
