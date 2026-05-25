import Carousel from '../Carousel/Carousel.tsx'
import NavButtons from '../NavButtons/NavButtons'
import CategoryCard from '../CategoryCard/CategoryCard'
import type { CategoryResponse } from '../../types'
import './CategoriesCarousel.css'

interface CategoriesCarouselProps {
  categories: CategoryResponse[]
}

export default function CategoriesCarousel({ categories }: CategoriesCarouselProps) {
  return (
    <div className="categories-carousel">
      <Carousel
        intervalMs={4500}
        ariaLabel="Categories"
        trackVariant="carousel-track--categories"
        renderHeader={({ next, prev }) => (
          <header className="categories-header carousel-header">
            <div>
              <h2 className="categories-title">Categories</h2>
              <p className="categories-subtitle">Tap to explore</p>
            </div>
            <NavButtons onPrev={prev} onNext={next} labelContext="categories" />
          </header>
        )}
      >
        {categories.length === 0 ? (
          <div className="categories-empty">No categories yet</div>
        ) : (
          categories.map((c) => (
            <CategoryCard
              key={c.id}
              id={c.id}
              name={c.name}
              taskCount={c.tasks?.filter((t) => t.status === 'ACTIVE').length ?? 0}
            />
          ))
        )}
      </Carousel>
    </div>
  )
}
