import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { resendConfirmation } from '../../api/auth';
import Arrival from '../../components/shared/Arrival/Arrival';
import DescentStage from '../../components/shared/DescentStage/DescentStage';
import ResendButton from '../../components/shared/ResendButton/ResendButton';
import StageLink from '../../components/shared/StageLink/StageLink';
import StepFlow from '../../components/shared/StepFlow/StepFlow';
import { useStepFlow } from '../../components/shared/StepFlow/useStepFlow';
import type { StepValues } from '../../components/shared/StepFlow/useStepFlow';
import { ROUTES } from '../../routes';
import { readPrefilledEmail } from '../../utils/resendPrefill';


function ResendPage() {
    const location = useLocation();
    const [dispatchedEmail, setDispatchedEmail] = useState<string | null>(null);
    const stepFlow = useStepFlow('resend', handleComplete, { email: readPrefilledEmail(location.state) });

    // The gateway answers 202 whether or not the account exists, so arrival never reveals which.
    async function handleComplete(values: StepValues) {
        await resendConfirmation(values.email);

        setDispatchedEmail(values.email);
    }

    const loginFooter = <StageLink to={ROUTES.login} variant="footer">LOG IN</StageLink>;

    if (dispatchedEmail !== null) {
        return (
            <DescentStage
                depthMetres={stepFlow.flow.seafloorDepthMetres}
                seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
                footer={loginFooter}
            >
                <Arrival
                    eyebrow="LINK DISPATCHED"
                    headline="It's on its way."
                    detail="IF AN ACCOUNT IS WAITING, A NEW LINK IS ON ITS WAY"
                >
                    <ResendButton email={dispatchedEmail} startsInCooldown />
                </Arrival>
            </DescentStage>
        );
    }

    return (
        <DescentStage
            depthMetres={stepFlow.depthMetres}
            seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
            isAlarm={stepFlow.isAlarm}
            footer={loginFooter}
        >
            <StepFlow state={stepFlow} />
        </DescentStage>
    );
}

export default ResendPage;
