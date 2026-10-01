import { useState } from 'react';
import { register } from '../../api/auth';
import Arrival from '../../components/shared/Arrival/Arrival';
import DescentStage from '../../components/shared/DescentStage/DescentStage';
import ResendButton from '../../components/shared/ResendButton/ResendButton';
import StageLink from '../../components/shared/StageLink/StageLink';
import StepFlow from '../../components/shared/StepFlow/StepFlow';
import { useStepFlow } from '../../components/shared/StepFlow/useStepFlow';
import type { StepValues } from '../../components/shared/StepFlow/useStepFlow';
import { ROUTES } from '../../routes';


function RegisterPage() {
    const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
    const stepFlow = useStepFlow('register', handleComplete);

    async function handleComplete(values: StepValues) {
        const authUser = await register({
            email: values.email,
            username: values.username,
            password: values.password,
        });

        setRegisteredEmail(authUser.email);
    }

    const loginFooter = (
        <>
            ALREADY ABOARD?
            <StageLink to={ROUTES.login} variant="footer">LOG IN</StageLink>
        </>
    );

    if (registeredEmail !== null) {
        return (
            <DescentStage
                depthMetres={stepFlow.flow.seafloorDepthMetres}
                seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
                footer={loginFooter}
            >
                <Arrival
                    eyebrow="ACCOUNT ESTABLISHED"
                    headline="Check your inbox."
                    detail={`CONFIRMATION SENT TO ${registeredEmail.toUpperCase()}`}
                >
                    <ResendButton email={registeredEmail} />
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

export default RegisterPage;
