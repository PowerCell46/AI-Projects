import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { createShortUrl } from '../../api/shortUrl'
import './LinkShortener.css'

type Phase = 'idle' | 'working' | 'done' | 'error'

const WORKING_DURATION_MS = 850
const COPY_DURATION_MS = 1500

function isUsableHost(hostname: string): boolean {
    if (hostname.includes('%')) {
        return false
    }
    return hostname === 'localhost' || hostname.includes('.')
}

function normalizeUrl(raw: string): string | null {
    const trimmed = raw.trim()
    if (!trimmed || /\s/.test(trimmed)) {
        return null
    }

    for (const candidate of [trimmed, `https://${trimmed}`]) {
        try {
            const parsed = new URL(candidate)
            if (
                (parsed.protocol === 'http:' || parsed.protocol === 'https:') &&
                isUsableHost(parsed.hostname)
            ) {
                return parsed.href
            }
        } catch {
            continue
        }
    }

    return null
}

function wait(ms: number): Promise<void> {
    return new Promise((resolve) => {
        setTimeout(resolve, ms)
    })
}

function LinkShortener() {
    const [phase, setPhase] = useState<Phase>('idle')
    const [inputValue, setInputValue] = useState('')
    const [shortUrl, setShortUrl] = useState('')
    const [errorMessage, setErrorMessage] = useState('')
    const [copied, setCopied] = useState(false)
    const [liveMessage, setLiveMessage] = useState('')

    const inputRef = useRef<HTMLInputElement>(null)
    const plateRef = useRef<HTMLDivElement>(null)
    const copyTimeoutRef = useRef<number | undefined>(undefined)

    useEffect(() => {
        return () => {
            if (copyTimeoutRef.current !== undefined) {
                window.clearTimeout(copyTimeoutRef.current)
            }
        }
    }, [])

    async function waitOutWorkingPhase(startedAt: number) {
        const elapsed = performance.now() - startedAt
        const remaining = WORKING_DURATION_MS - elapsed
        if (remaining > 0) {
            await wait(remaining)
        }
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        if (phase === 'working') {
            return
        }

        if (!inputValue.trim()) {
            return
        }

        const normalized = normalizeUrl(inputValue)
        if (!normalized) {
            setPhase('error')
            setErrorMessage("That doesn't look like a web address.")
            return
        }

        setPhase('working')
        setErrorMessage('')
        const startedAt = performance.now()

        try {
            const response = await createShortUrl(normalized)
            await waitOutWorkingPhase(startedAt)
            setShortUrl(response.shortUrl)
            setPhase('done')
            setLiveMessage(`Short link ready: ${response.shortUrl}`)
        } catch {
            await waitOutWorkingPhase(startedAt)
            setPhase('error')
            setErrorMessage("Couldn't reach the server. Try again.")
        }
    }

    function handleChange(event: ChangeEvent<HTMLInputElement>) {
        setInputValue(event.target.value)
        if (phase === 'error') {
            setPhase('idle')
            setErrorMessage('')
        }
    }

    function restartBounce() {
        const plate = plateRef.current
        if (!plate) {
            return
        }
        plate.classList.remove('bounce')
        void plate.offsetWidth
        plate.classList.add('bounce')
    }

    function handleCopy() {
        if (!shortUrl) {
            return
        }

        void navigator.clipboard.writeText(shortUrl)
        setCopied(true)
        setLiveMessage('Copied to clipboard')
        restartBounce()

        if (copyTimeoutRef.current !== undefined) {
            window.clearTimeout(copyTimeoutRef.current)
        }
        copyTimeoutRef.current = window.setTimeout(() => {
            setCopied(false)
        }, COPY_DURATION_MS)
    }

    function handleReset() {
        setInputValue('')
        setShortUrl('')
        setErrorMessage('')
        setCopied(false)
        setPhase('idle')
        inputRef.current?.focus()
    }

    const isEmpty = !inputValue.trim()
    const isWorking = phase === 'working'

    return (
        <div className="page" data-phase={phase}>
            <header className="wordmark">snip.sh</header>

            <main className="hero">
                <h1 className="headline">Long links go in. Short ones come out.</h1>

                <form className="plate-form" onSubmit={handleSubmit} noValidate>
                    <div className="plate" data-phase={phase} ref={plateRef}>
                        <div className="plate-row">
                            <div className="plate-input-wrap">
                                <label htmlFor="link-input" className="sr-only">
                                    Link to shorten
                                </label>
                                <input
                                    id="link-input"
                                    ref={inputRef}
                                    className="plate-input"
                                    type="text"
                                    inputMode="url"
                                    autoComplete="off"
                                    autoCapitalize="off"
                                    autoCorrect="off"
                                    spellCheck={false}
                                    placeholder="paste your link"
                                    value={inputValue}
                                    onChange={handleChange}
                                    disabled={isWorking}
                                />
                            </div>
                            <button
                                type="submit"
                                className="submit-btn"
                                disabled={isWorking}
                                data-empty={isEmpty}
                            >
                                {isWorking ? (
                                    <span className="dots" aria-hidden="true">
                                        <span className="dot" />
                                        <span className="dot" />
                                        <span className="dot" />
                                    </span>
                                ) : (
                                    <span className="submit-label">Shorten</span>
                                )}
                            </button>
                        </div>

                        <div className="press-head" aria-hidden="true" />

                        <button
                            type="button"
                            className="plate-result"
                            onClick={handleCopy}
                            tabIndex={phase === 'done' ? 0 : -1}
                        >
                            <span className="result-link">{shortUrl}</span>
                            <span className="result-meta">
                                {copied ? 'Copied' : 'Click to copy'}
                            </span>
                        </button>
                    </div>
                </form>

                <div className="action-row">
                    <button
                        type="button"
                        className="ghost-btn"
                        tabIndex={phase === 'done' ? 0 : -1}
                        onClick={handleReset}
                    >
                        Shorten another
                    </button>
                    <span className="hint">Links never expire</span>
                    <span className="error-msg">{phase === 'error' ? errorMessage : ''}</span>
                </div>
            </main>

            <footer className="footer">No account. No tracking.</footer>

            <div className="sr-only" role="status" aria-live="polite">
                {liveMessage}
            </div>
        </div>
    )
}

export default LinkShortener
