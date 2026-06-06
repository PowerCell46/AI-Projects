import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import styles from './PlaceholderPage.module.css'


interface PlaceholderPageProps {
  eyebrow: string
  title: string
}

/** Stand-in for sections whose backend endpoints don't exist yet. */
export const PlaceholderPage = ({ eyebrow, title }: PlaceholderPageProps) => (
  <>
    <PageHeader eyebrow={eyebrow} title={title} />
    <Panel>
      <p className={styles.message}>Coming soon.</p>
    </Panel>
  </>
)
