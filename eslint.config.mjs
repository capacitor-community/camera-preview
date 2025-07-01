import js from '@eslint/js';
import tseslint from '@typescript-eslint/eslint-plugin';
import parser from '@typescript-eslint/parser';

export default [
  js.configs.recommended,

  {
    files: ['**/*.ts'],
    languageOptions: {
      parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    plugins: {
      '@typescript-eslint': tseslint,
    },
    rules: {
      ...tseslint.configs.recommended.rules,
    },
  },

  // Web-specific override for browser globals
  {
    files: ['src/web.ts'],
    languageOptions: {
      globals: {
        window: 'readonly',
        navigator: 'readonly',
        document: 'readonly',
        HTMLVideoElement: 'readonly',
        MediaStream: 'readonly',
        MediaStreamConstraints: 'readonly',
        MediaTrackConstraints: 'readonly',
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off', // optional
    },
  },
];
