import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import stylistic from '@stylistic/eslint-plugin';
import { defineConfig, globalIgnores } from 'eslint/config';


export default defineConfig([
    globalIgnores(['dist']),
    {
        files: ['**/*.{ts,tsx}'],
        extends: [
            js.configs.recommended,
            tseslint.configs.recommended,
            reactHooks.configs.flat.recommended,
            reactRefresh.configs.vite,
        ],
        plugins: {
            '@stylistic': stylistic,
        },
        languageOptions: {
            globals: globals.browser,
        },
        rules: {
            // Project style: 4-space indent, always semicolons, single quotes.
            '@stylistic/indent': ['error', 4, { SwitchCase: 1 }],
            '@stylistic/semi': ['error', 'always'],
            '@stylistic/quotes': ['error', 'single', { avoidEscape: true }],
            '@stylistic/comma-dangle': ['error', 'always-multiline'],

            // Two blank lines between the last import and the first statement.
            '@stylistic/padding-line-between-statements': [
                'error',
                { blankLine: 'always', prev: 'import', next: '*' },
                { blankLine: 'any', prev: 'import', next: 'import' },
            ],

            // const over let, never var; no `any`.
            'prefer-const': 'error',
            'no-var': 'error',
            '@typescript-eslint/no-explicit-any': 'error',
        },
    },
]);
