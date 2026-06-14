Your task is to build a desktop application that allows users to open an existing image and transform it creatively. The goal is to encourage experimentation with visual expression through a collection of customizable transformation tools. Instead of passively viewing an image, users should be able to explore how various effects reshape its structure, mood, and overall appearance. Unlike traditional image editors that focus on precise adjustments or professional workflows, this application should emphasize exploration, playfulness, and artistic experimentation.

You will design an intuitive, visually appealing desktop application that enables users to apply different transformations, analyze visual patterns, and convert images into alternative visual formats (such as ASCII-art representations).


Name: TransformIt

Users should be able to:

· Open an existing image file from their device

· Preview the selected image before and after applying transformations

· Apply a transformation that converts the image to grayscale

· Apply a transformation that converts the image into a grid of ASCII characters while maintaining recognizable visual structure

· Apply a transformation that flips/mirrors the image

· Apply a transformation that swaps/shifts colors from the image based on predefined color palettes or automatically generated color distribution tables

· Adjust transformation parameters (e.g. ASCII density, brightness, grid size) through user-friendly controls

· Combine multiple filters and preview the output

· Export the generated ASCII art as a “txt” file

· Export the transformed image as a “png” file

· Enjoy using your application

By completing this project, you will:

· Manipulate images and explore how various file formats represent image data in memory

· Build a modern desktop application

· Handle local file I/O (opening, exporting)

· Practice structuring a multi-feature application with modular components

· Explore algorithmic creativity


Detailed functional requirements:

· An “Open Image” button should allow users to load an image from their local machine (through the native file picker dialog).

· The selected image should be rendered in a dedicated workspace area.

· Offer a clear interface for selecting and configuring transformations.

· Implement an ASCII conversion feature by converting pixel(s) to characters according to their color/brightness. The character density should be adjustable (the density is the area represented by each character – 1x1, 5x5, 10x10, etc.). Strive for preservation of recognizable shapes as much as possible.

· Implement grayscale transformation.

· Implement flip/mirror transformation (both horizontally and vertically).

· Implement a color transformation that analyzes the image’s color distribution and swaps high-frequency colors with low-frequency ones to produce unique visual results.

· Allow users to apply multiple transformations sequentially.

· Provide an option to export the generated ASCII art or the final transformed image.

· Ensure that images are consistently displayed in an appropriate size and aspect ratio, avoiding issues such as cropping, overflow, or disproportionate scaling.

Non-functional requirements:

· The application should be cross-platform

· The application should feel smooth and responsive

· The application should not freeze during heavy transformations (heavy operations should show a loading indicator instead of blocking the app)

· Keep the interface clean, accessible, and easy to navigate

· Avoid unnecessary memory use when processing images

· Code should be organized, maintainable, and well-documented