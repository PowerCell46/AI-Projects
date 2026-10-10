import { render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { useFocusOnMount } from './useFocusOnMount';


function Heading() {
    const headingRef = useFocusOnMount<HTMLHeadingElement>();

    return <h1 ref={headingRef} tabIndex={-1}>Opened page</h1>;
}

afterEach(() => {
    document.body.innerHTML = '';
});

describe('useFocusOnMount', () => {
    it('should_focus_the_heading_when_nothing_holds_focus', () => {
        const { getByRole } = render(<Heading />);

        expect(document.activeElement).toBe(getByRole('heading', { name: 'Opened page' }));
    });

    it('should_focus_the_heading_when_the_focused_element_is_hidden', () => {
        const hiddenPanel = document.createElement('section');
        hiddenPanel.hidden = true;
        const hiddenButton = document.createElement('button');
        hiddenPanel.append(hiddenButton);
        document.body.append(hiddenPanel);
        hiddenButton.focus();

        const { getByRole } = render(<Heading />);

        expect(document.activeElement).toBe(getByRole('heading', { name: 'Opened page' }));
    });

    it('should_leave_focus_alone_when_it_sits_on_an_element_still_on_screen', () => {
        const visibleButton = document.createElement('button');
        document.body.append(visibleButton);
        visibleButton.focus();

        render(<Heading />);

        expect(document.activeElement).toBe(visibleButton);
    });
});
