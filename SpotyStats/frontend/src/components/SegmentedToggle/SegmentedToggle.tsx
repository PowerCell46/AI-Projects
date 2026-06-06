import styles from './SegmentedToggle.module.css'


interface SegmentedToggleProps<T extends string> {
  options: readonly T[]
  value: T
  onChange: (value: T) => void
}

export const SegmentedToggle = <T extends string>({
  options,
  value,
  onChange,
}: SegmentedToggleProps<T>) => (
  <div className={styles.container} role="tablist">
    {options.map((option) => (
      <button
        key={option}
        role="tab"
        aria-selected={option === value}
        className={option === value ? styles.optionActive : styles.option}
        onClick={() => onChange(option)}
      >
        {option}
      </button>
    ))}
  </div>
)
