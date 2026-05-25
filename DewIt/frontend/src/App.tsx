import { Routes, Route } from 'react-router-dom'

import Dashboard from './pages/Dashboard/Dashboard'
import CategoryView from './pages/CategoryView/CategoryView'
import NewTaskView from './pages/NewTaskView/NewTaskView'
import Statistics from './pages/Statistics/Statistics'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/categories/:id" element={<CategoryView />} />
      <Route path="/tasks/new" element={<NewTaskView />} />
      <Route path="/stats" element={<Statistics />} />
    </Routes>
  )
}
