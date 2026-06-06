import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'


interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
  children: ReactNode
}

export const Button = ({ variant = 'primary', children, ...rest }: ButtonProps) => (
  <button
    className={variant === 'primary' ? styles.primary : styles.secondary}
    {...rest}
  >
    {children}
  </button>
)
