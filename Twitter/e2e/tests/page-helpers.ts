import { expect, type Locator, type Page, type Response } from '@playwright/test';

export const COMPOSER_PLACEHOLDER = "What's worth sending up?";

export function postWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article')
        .filter({ hasText: text });
}

export function waitForCall(page: Page, method: string, urlPart: string): Promise<Response> {
    return page.waitForResponse((response) => {
        const requestMethod = response
            .request()
            .method();
        const responseUrl = response.url();

        return requestMethod === method && responseUrl.includes(urlPart);
    });
}

export function firstPost(page: Page): Locator {
    return page
        .getByRole('article')
        .first();
}

export async function expectImageToLoad(image: Locator) {
    await expect
        .poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth))
        .toBeGreaterThan(0);
}

// WCAG 2.5.5 and the project rule: a touch target is at least 44x44 CSS px.
export const MIN_TOUCH_TARGET_PX = 44;

const INTERACTIVE_SELECTOR = 'button, a[href], input, select, textarea, [role=button], [role=tab], [role=link]';

const MAIN_ELEMENT_SELECTOR = 'main, header, nav, footer, form, article, [role=dialog]';

interface TouchTargetCheck {
    selector: string;
    minimumSize: number;
}

// Collects every visible interactive element under the root that is smaller than the minimum, so one failure names
// them all. Two narrow exemptions: an inline link inside a paragraph (WCAG's inline-text exception), and a visually
// hidden (1px) input, whose visible label is measured in its place because the label is what a finger presses.
async function smallTouchTargets(root: Locator): Promise<string[]> {
    const check: TouchTargetCheck = {
        selector: INTERACTIVE_SELECTOR,
        minimumSize: MIN_TOUCH_TARGET_PX,
    };

    return root.evaluate(
        (rootElement, { selector, minimumSize }) => {
            const offenders: string[] = [];

            function isVisible(element: Element): boolean {
                const style = getComputedStyle(element);
                const box = element.getBoundingClientRect();

                return style.display !== 'none'
                    && style.visibility !== 'hidden'
                    && box.width > 0
                    && box.height > 0;
            }

            function isInlineLinkInParagraph(element: Element): boolean {
                return element.tagName === 'A'
                    && getComputedStyle(element).display === 'inline'
                    && element.closest('p') !== null;
            }

            function pressedElement(element: Element): Element {
                const box = element.getBoundingClientRect();
                const isVisuallyHidden = box.width <= 1 && box.height <= 1;

                if (!(element instanceof HTMLInputElement) || !isVisuallyHidden) {
                    return element;
                }

                return [...(element.labels ?? [])].find(isVisible) ?? element;
            }

            function describe(element: Element, width: number, height: number): string {
                const fieldLabel = element instanceof HTMLInputElement ? element.labels?.[0]?.textContent : null;
                const label = element.getAttribute('aria-label')
                    ?? fieldLabel
                    ?? element.getAttribute('placeholder')
                    ?? (element.textContent ?? '').trim().slice(0, 30);
                const role = element.getAttribute('role') ?? element.getAttribute('type') ?? '';
                const classes = element.getAttribute('class') ?? '';

                return `<${element.tagName.toLowerCase()}${role ? ` ${role}` : ''}> "${label}" .${classes} ${Math.round(width)}x${Math.round(height)}`;
            }

            const candidates = rootElement.matches(selector)
                ? [rootElement, ...rootElement.querySelectorAll(selector)]
                : [...rootElement.querySelectorAll(selector)];

            for (const element of candidates) {
                if (!isVisible(element) || isInlineLinkInParagraph(element)) {
                    continue;
                }

                const target = pressedElement(element);
                const { width, height } = target.getBoundingClientRect();

                if (width < minimumSize || height < minimumSize) {
                    offenders.push(describe(target, width, height));
                }
            }

            return offenders;
        },
        check,
    );
}

// Every visible button, link, field and tab (inside the root, the whole page by default) is at least 44x44 CSS px.
export async function expectTouchTargetsAtLeast44px(page: Page, root: Locator = page.locator('body')) {
    expect(await smallTouchTargets(root), 'interactive elements smaller than 44x44 CSS px').toEqual([]);
}

// The page does not scroll sideways, and no main element is wider than the viewport.
export async function expectNoHorizontalOverflow(page: Page) {
    const overflowing = await page.evaluate((selector) => {
        const viewportWidth = document.documentElement.clientWidth;
        const wide: string[] = [];

        if (document.documentElement.scrollWidth > viewportWidth) {
            wide.push(`document scrollWidth ${document.documentElement.scrollWidth} > clientWidth ${viewportWidth}`);
        }

        for (const element of document.querySelectorAll(selector)) {
            const box = element.getBoundingClientRect();

            if (box.width > viewportWidth) {
                wide.push(`<${element.tagName.toLowerCase()}> .${element.getAttribute('class') ?? ''} ${Math.round(box.width)}px wide`);
            }
        }

        return wide;
    }, MAIN_ELEMENT_SELECTOR);

    expect(overflowing, 'horizontal overflow').toEqual([]);
}
