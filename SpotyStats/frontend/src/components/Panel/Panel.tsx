import type { ReactNode } from 'react'
import styles from './Panel.module.css'


interface PanelProps {
  /** Extra class merged onto the panel surface, e.g. for page-specific layout. */
  className?: string
  children: ReactNode
}

/** Standard glass surface: panel bg, border, blur, radius 20, padding 32/32/28. */
export const Panel = ({ className, children }: PanelProps) => (
  <section className={className !== undefined ? `${styles.panel} ${className}` : styles.panel}>
    {children}
  </section>
)
