import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'


interface PageHeaderProps {
  eyebrow: string
  title: string
  actions?: ReactNode
}

export const PageHeader = ({ eyebrow, title, actions }: PageHeaderProps) => (
  <header className={styles.header}>
    <div>
      <div className={styles.eyebrow}>{eyebrow}</div>
      <h1 className={styles.title}>{title}</h1>
    </div>
    {actions !== undefined && <div className={styles.actions}>{actions}</div>}
  </header>
)
