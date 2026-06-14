# TransformIt — Assignment Brief

> The original task description, kept verbatim in substance as the source of requirements.
> For how it's being built, see [PROJECT.md](PROJECT.md).

## Goal

Build a desktop application that lets users open an existing image and transform it creatively. The
aim is to encourage experimentation with visual expression through a collection of customizable
transformation tools. Instead of passively viewing an image, users explore how various effects
reshape its structure, mood, and overall appearance.

Unlike traditional image editors focused on precise adjustments or professional workflows, this app
emphasizes **exploration, playfulness, and artistic experimentation** — applying transformations,
analyzing visual patterns, and converting images into alternative formats (such as ASCII art).

**Name:** TransformIt

## Users should be able to

- Open an existing image file from their device.
- Preview the selected image before and after applying transformations.
- Convert the image to grayscale.
- Convert the image into a grid of ASCII characters while keeping a recognizable visual structure.
- Flip / mirror the image.
- Swap or shift colors based on predefined palettes or automatically generated color-distribution tables.
- Adjust transformation parameters (ASCII density, brightness, grid size) through friendly controls.
- Combine multiple filters and preview the output.
- Export the generated ASCII art as a `.txt` file.
- Export the transformed image as a `.png` file.

## Functional requirements

- An **Open Image** button loads an image from the local machine via the native file-picker dialog.
- The selected image renders in a dedicated workspace area.
- A clear interface for selecting and configuring transformations.
- **ASCII conversion:** map pixel blocks to characters by color/brightness. The character density is
  adjustable — the area each character represents (1×1, 5×5, 10×10, …). Preserve recognizable shapes
  as much as possible.
- **Grayscale** transformation.
- **Flip / mirror** transformation, both horizontally and vertically.
- **Color transformation:** analyze the image's color distribution and swap high-frequency colors
  with low-frequency ones to produce unique results.
- Apply multiple transformations sequentially.
- Export the generated ASCII art or the final transformed image.
- Display images at an appropriate size and aspect ratio — no cropping, overflow, or disproportionate
  scaling.

## Non-functional requirements

- **Cross-platform.**
- Smooth and responsive; never freezes during heavy transformations (show a loading indicator instead
  of blocking the app).
- Clean, accessible, easy-to-navigate interface.
- Avoid unnecessary memory use when processing images.
- Organized, maintainable, well-documented code.

## Learning outcomes

By completing this project you will:

- Manipulate images and explore how file formats represent image data in memory.
- Build a modern desktop application.
- Handle local file I/O (opening, exporting).
- Structure a multi-feature application with modular components.
- Explore algorithmic creativity.
