import js from '@eslint/js'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['src/**/*.{js,jsx}'],
    ...js.configs.recommended,
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: {
        window: 'readonly',
        document: 'readonly',
        localStorage: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        FormData: 'readonly',
        Date: 'readonly',
        Math: 'readonly',
        Number: 'readonly',
        Boolean: 'readonly',
        console: 'readonly',
        Promise: 'readonly',
      },
    },
    plugins: { react, 'react-hooks': reactHooks },
    settings: { react: { version: 'detect' } },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      // The JSX transform makes the React import unnecessary, and prop-types
      // are redundant in a codebase this small with no public component API.
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      // Rendering a list keyed by index is correct here: the parse viewer's
      // lines are a fixed, ordered, never-reordered array.
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
]
