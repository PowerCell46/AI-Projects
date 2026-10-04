import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { login } from '../../api/auth';
import type { AuthUser } from '../../api/auth';
import Arrival from '../../components/shared/Arrival/Arrival';
import DescentStage from '../../components/shared/DescentStage/DescentStage';
import StageLink from '../../components/shared/StageLink/StageLink';
import StepFlow from '../../components/shared/StepFlow/StepFlow';
import { useStepFlow } from '../../components/shared/StepFlow/useStepFlow';
import type { StepValues } from '../../components/shared/StepFlow/useStepFlow';
import { useAuth } from '../../contexts/AuthContext';
import { ROUTES } from '../../routes';
import { isAscentLocationState } from '../../utils/ascent';
import { prefersReducedMotion } from '../../utils/motion';
import { buildResendState } from '../../utils/resendPrefill';


// The arrival rule's 150ms delay plus its 1300ms draw in Arrival.css; the session is established when it ends.
const ARRIVAL_RULE_DRAW_MS = 1450;

// The 500ms opacity transition in DescentStage.css; the stage has faded out when the session is established.
const STAGE_LEAVE_MS = 500;

// After signing out the stage fades in at the seafloor, then the gauge rises to the surface; the fade-in is 900ms.
const ASCENT_START_DELAY_MS = 400;

function LoginPage() {
    const { signIn } = useAuth();
    const [arrivedUser, setArrivedUser] = useState<AuthUser | null>(null);
    const [isLeaving, setIsLeaving] = useState(false);
    const location = useLocation();
    const [isFromFeed] = useState(() => isAscentLocationState(location.state));
    const [isAscending, setIsAscending] = useState(isFromFeed);
    const stepFlow = useStepFlow('login', handleComplete);

    useEffect(() => {
        if (!isAscending) {
            return;
        }

        const ascentTimerId = window.setTimeout(
            () => setIsAscending(false),
            prefersReducedMotion() ? 0 : ASCENT_START_DELAY_MS,
        );

        return () => window.clearTimeout(ascentTimerId);
    }, [isAscending]);

    useEffect(() => {
        if (!arrivedUser) {
            return;
        }

        const shouldAnimate = !prefersReducedMotion();
        const drawDurationMs = shouldAnimate ? ARRIVAL_RULE_DRAW_MS : 0;
        const leaveDurationMs = shouldAnimate ? STAGE_LEAVE_MS : 0;

        const leaveTimerId = window.setTimeout(() => setIsLeaving(true), drawDurationMs);

        // Signing in makes GuestRoute send the user on to the feed.
        const signInTimerId = window.setTimeout(
            () => signIn(arrivedUser),
            drawDurationMs + leaveDurationMs,
        );

        return () => {
            window.clearTimeout(leaveTimerId);
            window.clearTimeout(signInTimerId);
        };
    }, [arrivedUser, signIn]);

    async function handleComplete(values: StepValues) {
        const authUser = await login({
            identifier: values.identifier,
            password: values.password,
        });

        setArrivedUser(authUser);
    }

    if (arrivedUser) {
        return (
            <DescentStage
                depthMetres={stepFlow.flow.seafloorDepthMetres}
                seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
                isLeaving={isLeaving}
            >
                <Arrival
                    eyebrow="IDENTITY CONFIRMED"
                    headline={<>Seafloor reached.<br />Welcome back down.</>}
                />
            </DescentStage>
        );
    }

    const resendLink = stepFlow.offersResend && (
        <StageLink
            to={ROUTES.resend}
            variant="secondary"
            state={buildResendState(stepFlow.values.identifier)}
        >
            RESEND LINK
        </StageLink>
    );

    return (
        <DescentStage
            depthMetres={isAscending ? stepFlow.flow.seafloorDepthMetres : stepFlow.depthMetres}
            seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
            isAlarm={stepFlow.isAlarm}
            isEntering={isFromFeed}
            footer={
                <>
                    NEW HERE?
                    <StageLink to={ROUTES.register} variant="footer">CREATE ACCOUNT</StageLink>
                </>
            }
        >
            <StepFlow state={stepFlow} errorAction={resendLink} />
        </DescentStage>
    );
}

export default LoginPage;
