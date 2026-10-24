import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/** One place for the accent, surfaces, text, borders and radius (docs/design/ui-tokens.md). Light only. */
export const CampusFlowPreset = definePreset(Aura, {
  primitive: {
    borderRadius: {
      none: '0',
      xs: '4px',
      sm: '10px',
      md: '10px',
      lg: '16px',
      xl: '16px',
    },
  },
  semantic: {
    primary: {
      50: '#F4F0FD',
      100: '#ECE6FB',
      200: '#DCD0F8',
      300: '#C4B0F2',
      400: '#9B7FE9',
      500: '#6D4AE0',
      600: '#5A3BC4',
      700: '#4B2FA8',
      800: '#3A2582',
      900: '#2B1B60',
      950: '#1B1140',
    },
    formField: {
      paddingX: '0.875rem',
      paddingY: '0.75rem',
      borderRadius: '10px',
    },
    colorScheme: {
      light: {
        surface: {
          0: '#FFFFFF',
          50: '#F7F5FD',
          100: '#EDE7FB',
          200: '#E6E0F5',
          300: '#CFC7E6',
          400: '#8E84AD',
          500: '#6A5F8C',
          600: '#5A5075',
          700: '#3B3157',
          800: '#2A2245',
          900: '#1E1833',
          950: '#120D22',
        },
        primary: {
          color: '#6D4AE0',
          contrastColor: '#FFFFFF',
          hoverColor: '#5A3BC4',
          activeColor: '#4B2FA8',
        },
        highlight: {
          background: '#ECE6FB',
          focusBackground: '#E6DEFB',
          color: '#4B2FA8',
          focusColor: '#4B2FA8',
        },
        text: {
          color: '#1E1833',
          hoverColor: '#1E1833',
          mutedColor: '#5A5075',
          hoverMutedColor: '#3B3157',
        },
        content: {
          background: '#FFFFFF',
          hoverBackground: '#F7F5FD',
          borderColor: '#E6E0F5',
          color: '#1E1833',
          hoverColor: '#1E1833',
        },
        formField: {
          background: '#FFFFFF',
          disabledBackground: '#F7F5FD',
          borderColor: '#8E84AD',
          hoverBorderColor: '#6D4AE0',
          focusBorderColor: '#6D4AE0',
          color: '#1E1833',
          placeholderColor: '#5A5075',
        },
      },
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '10px',
        paddingY: '0.75rem',
      },
    },
    card: {
      root: {
        borderRadius: '16px',
      },
    },
  },
});
