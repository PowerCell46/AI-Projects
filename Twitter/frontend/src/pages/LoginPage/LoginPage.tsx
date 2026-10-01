import { useEffect, useState } from 'react';
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
import { prefersReducedMotion } from '../../utils/motion';
import { buildResendState } from '../../utils/resendPrefill';


// The arrival rule's 150ms delay plus its 1300ms draw in Arrival.css; the session is established when it ends.
const ARRIVAL_RULE_DRAW_MS = 1450;

function LoginPage() {
    const { signIn } = useAuth();
    const [arrivedUser, setArrivedUser] = useState<AuthUser | null>(null);
    const stepFlow = useStepFlow('login', handleComplete);

    useEffect(() => {
        if (!arrivedUser) {
            return;
        }

        // Signing in makes GuestRoute send the user on to the feed.
        const drawTimerId = window.setTimeout(
            () => signIn(arrivedUser),
            prefersReducedMotion() ? 0 : ARRIVAL_RULE_DRAW_MS,
        );

        return () => window.clearTimeout(drawTimerId);
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
            depthMetres={stepFlow.depthMetres}
            seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
            isAlarm={stepFlow.isAlarm}
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
