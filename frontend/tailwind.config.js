/**
 * The brand.
 *
 * Ink is a near-black navy rather than pure black: on a white page pure black
 * has more contrast than the eye wants for body text, and reads as harsh over a
 * long report — which is exactly what this product asks people to read.
 *
 * The accent is a considered blue. It has to sit next to a red "critical"
 * badge and a green "pass" badge without competing with either, because a
 * finding list shows all three at once.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          50: '#f5f7fa',
          100: '#e9edf4',
          200: '#cfd8e6',
          300: '#a6b5cd',
          400: '#748bae',
          500: '#526b93',
          600: '#3f547a',
          700: '#344563',
          800: '#2d3b53',
          900: '#1b2438',
          950: '#111725',
        },
        brand: {
          50: '#eef6ff',
          100: '#d9eaff',
          200: '#bcdbff',
          300: '#8ec5ff',
          400: '#59a5ff',
          500: '#3182f6',
          600: '#1c63eb',
          700: '#164ed8',
          800: '#1841af',
          900: '#1a3b8a',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
        mono: [
          'ui-monospace',
          'SFMono-Regular',
          'Menlo',
          'Consolas',
          'Liberation Mono',
          'monospace',
        ],
      },
      boxShadow: {
        card: '0 1px 2px rgba(17, 23, 37, 0.04), 0 4px 16px rgba(17, 23, 37, 0.06)',
        lift: '0 8px 30px rgba(17, 23, 37, 0.10)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.25s ease-out both',
      },
    },
  },
  plugins: [],
}
