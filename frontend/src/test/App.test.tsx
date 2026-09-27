import { render, screen } from '@testing-library/react'
import App from '../App'

describe('App', () => {
  it('muestra el nombre de la plataforma', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Plataforma AS2/EDI' })).toBeInTheDocument()
  })
})
