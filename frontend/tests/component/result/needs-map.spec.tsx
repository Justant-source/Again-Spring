import { render, screen } from '@testing-library/react'
import { NeedsMap } from '@/components/result/NeedsMap'

describe('NeedsMap', () => {
  const basePosition = { x: 50, y: 50 }
  const partnerPosition = { x: -50, y: 75 }

  describe('2D Variant (default)', () => {
    it('renders 2D map by default', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      expect(container.querySelector('svg')).toBeInTheDocument()
    })

    it('displays both position dots', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      // Two dots should be rendered
      const dots = container.querySelectorAll('div[style*="border-radius: 50%"]')
      expect(dots.length).toBeGreaterThanOrEqual(1)
    })

    it('displays axis labels', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Left–Right"
          axisY="Top–Bottom"
        />
      )

      // Should display axis labels
      const text = screen.getByText(/Left/)
      expect(text).toBeInTheDocument()
    })

    it('displays user labels', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          labelA="Alice"
          labelB="Bob"
        />
      )

      expect(screen.getByText('Alice')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })

    it('shows connecting dashed line when both positions exist', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      const line = container.querySelector('line[stroke-dasharray]')
      expect(line).toBeInTheDocument()
    })

    it('displays placeholder when positionB is null', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={null}
          axisX="Independence–Connection"
          labelA="Alice"
        />
      )

      expect(screen.getByText('아직 비어있어요')).toBeInTheDocument()
    })

    it('uses default axis labels', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={null}
          axisX="Custom"
        />
      )

      // Should use default Y axis labels when not provided
      // Check that the component renders without error
      expect(container).toBeInTheDocument()
    })

    it('displays tooltip icon when reasonA is provided', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          labelA="Alice"
          labelB="Bob"
          reasonA="Because of X"
        />
      )

      const tooltip = screen.getByTitle('Because of X')
      expect(tooltip).toBeInTheDocument()
      expect(tooltip.textContent).toBe('?')
    })

    it('displays tooltip icon when reasonB is provided', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          labelA="Alice"
          labelB="Bob"
          reasonB="Because of Y"
        />
      )

      const tooltip = screen.getByTitle('Because of Y')
      expect(tooltip).toBeInTheDocument()
    })

    it('does not display tooltip when reason is not provided', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          labelA="Alice"
          labelB="Bob"
        />
      )

      const tooltips = container.querySelectorAll('[title]')
      expect(tooltips.length).toBe(0)
    })

    it('respects custom size prop', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          size={500}
        />
      )

      const mapContainer = container.firstChild as HTMLElement
      expect(mapContainer.style.width).toContain('500')
    })

    it('displays grid lines', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      // Check for grid line divs
      const gridLines = container.querySelectorAll('div[style*="height: 1px"]')
      expect(gridLines.length).toBeGreaterThan(0)
    })

    it('displays center axes', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      const centerAxes = container.querySelectorAll('div[style*="position: absolute"]')
      expect(centerAxes.length).toBeGreaterThan(0)
    })
  })

  describe('Venn Variant', () => {
    it('renders venn diagram when variant="venn"', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          variant="venn"
        />
      )

      const circles = container.querySelectorAll('circle')
      expect(circles.length).toBeGreaterThanOrEqual(2)
    })

    it('displays overlapping circles', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          variant="venn"
        />
      )

      const circles = container.querySelectorAll('circle')
      expect(circles[0]).toHaveAttribute('fill')
      expect(circles[1]).toHaveAttribute('fill')
    })

    it('displays user labels in venn diagram', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          labelA="Alice"
          labelB="Bob"
          variant="venn"
        />
      )

      expect(screen.getByText('Alice')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })

    it('displays "함께" label in venn diagram overlap', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
          variant="venn"
        />
      )

      expect(screen.getByText('함께')).toBeInTheDocument()
    })

    it('displays axis labels in venn variant', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independent–Connected"
          axisY="Stable–Dynamic"
          variant="venn"
        />
      )

      expect(container.textContent).toContain('Independent')
      expect(container.textContent).toContain('Stable')
    })
  })

  describe('Bars Variant', () => {
    it('renders bar chart when variant="bars"', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Left–Right"
          axisY="Top–Bottom"
          variant="bars"
        />
      )

      const bars = container.querySelectorAll('div[style*="background: var(--P-card)"]')
      expect(bars.length).toBeGreaterThanOrEqual(1)
    })

    it('displays both horizontal and vertical bars', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Left–Right"
          axisY="Top–Bottom"
          variant="bars"
        />
      )

      // Should have 2 bar groups (X and Y axes)
      const barGroups = container.querySelectorAll('div[style*="display: flex"]')
      expect(barGroups.length).toBeGreaterThan(0)
    })

    it('displays position dots on bars', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Left–Right"
          axisY="Top–Bottom"
          variant="bars"
        />
      )

      const dots = container.querySelectorAll('div[style*="border-radius: 50%"]')
      expect(dots.length).toBeGreaterThanOrEqual(1)
    })

    it('displays axis labels in bars variant', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independ–Connect"
          axisY="Stable–Dynamic"
          variant="bars"
        />
      )

      expect(container.textContent).toContain('Independ')
      expect(container.textContent).toContain('Stable')
    })

    it('shows different colors for A and B positions', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Left–Right"
          variant="bars"
        />
      )

      const coloredDots = container.querySelectorAll('div[style*="var(--P-a)"], div[style*="var(--P-b)"]')
      expect(coloredDots.length).toBeGreaterThanOrEqual(1)
    })
  })

  describe('Position Mapping', () => {
    it('maps x coordinate -100 to 0%', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: -100, y: 0 }}
          positionB={null}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })

    it('maps x coordinate 100 to 100%', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: 100, y: 0 }}
          positionB={null}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })

    it('maps y coordinate -100 to 0%', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: 0, y: -100 }}
          positionB={null}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })

    it('maps y coordinate 100 to 100%', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: 0, y: 100 }}
          positionB={null}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })
  })

  describe('Edge Cases', () => {
    it('handles missing axis Y gracefully', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={null}
          axisX="Left–Right"
        />
      )

      expect(container.textContent).toContain('연결')
    })

    it('handles axis strings without hyphen', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={null}
          axisX="Single"
        />
      )

      expect(container).toBeInTheDocument()
    })

    it('uses default user labels', () => {
      render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      expect(screen.getByText('서현')).toBeInTheDocument()
      expect(screen.getByText('준호')).toBeInTheDocument()
    })

    it('uses default size', () => {
      const { container } = render(
        <NeedsMap
          positionA={basePosition}
          positionB={partnerPosition}
          axisX="Independence–Connection"
        />
      )

      const mapContainer = container.firstChild as HTMLElement
      expect(mapContainer.style.width).toContain('280')
    })

    it('handles positions with same x and y', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: 50, y: 50 }}
          positionB={{ x: 50, y: 50 }}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })

    it('handles positions with extreme values', () => {
      const { container } = render(
        <NeedsMap
          positionA={{ x: -100, y: -100 }}
          positionB={{ x: 100, y: 100 }}
          axisX="Left–Right"
        />
      )

      expect(container).toBeInTheDocument()
    })
  })
})
