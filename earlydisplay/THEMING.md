# Early Loading Screen Themes

This document describes how to customize the theme for the FML early loading screen.

## Themes

FML will try to load one of the following themes.

| Theme Id      | Description                                                                           | Filename                                                                                                   |
|---------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------| 
| `default`     | Loaded if no other criteria are met.                                                  | [theme-default.json](./src/main/resources/net/neoforged/fml/earlydisplay/theme/theme-default.json)         |
| `darkmode`    | Loaded if the player configured dark-mode in options.txt or via environment variable. | [theme-darkmode.json](./src/main/resources/net/neoforged/fml/earlydisplay/theme/theme-darkmode.json)       |
| `april-fools` | Loaded on April 1st. Takes precedence over the other themes.                          | [theme-april-fools.json](./src/main/resources/net/neoforged/fml/earlydisplay/theme/theme-april-fools.json) |

## Customization

FML will first try to load resources or themes from the `config/fml/` subfolder, relative to the game directory.

If resources aren't found in that folder, it will load them from
the [resources bundled with FML](./src/main/resources/net/neoforged/fml/earlydisplay/theme/) instead.

## Layout

### Loading Screen

The loading screen is laid out using a 854 by 480 layout pixel sized rectangle. This rectangle is centered to fill
the available screen size.

## Theme Structure

The theme JSON files have the following keys:

| Property        | Type                                | Description                                                                                                                                           |
|-----------------|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `version`       | number                              | Must be `1`.                                                                                                                                          |
| `extends`       | string                              | Allows you to base your theme on another existing theme. Most commonly your themes will use `builtin:default` as the base theme.                      |
| `windowIcon`    | [resource](#resource)               | Sets the icon of the operating system window while the game is loading.                                                                               |
| `fonts`         | dictionary of [resource](#resource) | Fonts. Must contain a font named `default`.                                                                                                           |
| `shaders`       | dictionary of [resource](#resource) | Shaders. Must contain a `gui` shader (for drawing textured rectangles), `font` shader for drawing text and `color` for drawing untextured rectangles. |
| `colorScheme`   | [color scheme](#color-scheme)       | The color scheme.                                                                                                                                     |
| `sprites`       | [sprites](#sprites)                 | Defines various sprites for use by widgets in the loading screen.                                                                                     |
| `loadingScreen` | [loading screen](#loading-screen)   | Configures elements of the loading screen.                                                                                                            |

### resource

References to resources are encoded as strings. To load a sprite from `test.png`, you'd specify `test.png` in your
theme.
As explained above, FML will first try to find `test.png` relative to your theme JSON, and if it can't find it there,
it'll try to use it from the bundled resources. This mechanism can also be used to simply override a bundled resource
by placing the override in the theme folder, without actually overriding the bundled theme itself.

### color scheme

The color type defines the following properties:

| Property           | Type            | Description                                                                                                                           |
|--------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `screenBackground` | [color](#color) | Used to fill the screen background.                                                                                                   |
| `text`             | [color](#color) | Default text color.                                                                                                                   |
| `memoryLowColor`   | [color](#color) | The color to use for coloring the bar when resource usage is low. The actual color will be interpolated between this and `highColor`. |
| `memoryHighColor`  | [color](#color) | The color to use for coloring the bar when resource usage is high. The actual color will be interpolated between this and `lowColor`. |

### color

Colors are specified in a HTML-like format: `#AARRGGBB` or `#RRGGBB`.

### sprites

The sprites type defines the following properties:

| Property                          | Type                | Description                                                                                                                                           |
|-----------------------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `progressBarBackground`           | [texture](#texture) | This sprite is drawn to fill the background of progress bars.                                                                                         |
| `progressBarForeground`           | [texture](#texture) | The sprite to draw as the foreground of progress bars using coverage corresponding to the progress.                                                   |
| `progressBarIndeterminate`        | [texture](#texture) | The sprite to draw as the foreground of indeterminate progress bars.                                                                                  |
| `progressBarIndeterminateBounces` | boolean             | If true, an indeterminate progress bar will bounce back and forth within the bounds instead of disappearing to the right and reappearing on the left. |

### texture

The texture type defines the following properties:

| Property    | Type                                      | Description                                                                                    |
|-------------|-------------------------------------------|------------------------------------------------------------------------------------------------|
| `resource`  | [resource](#resource)                     | The bitmap used for this texture. Currently only png files are supported.                      |
| `scaling`   | [texture scaling](#texture-scaling)       | Defines properties about how the bitmap is drawn to the screen given some arbitrary rectangle. |
| `animation` | [animation metadata](#animation-metadata) | Optional. Used to set up an animated texture.                                                  |

### texture scaling

The texture scaling can be configured as one of three types using the `type` field.

| Property        | Type      | Description                                                                                         |
|-----------------|-----------|-----------------------------------------------------------------------------------------------------|
| `type`          | `stretch` | The bitmap will simply be stretched to fill the rectangle.                                          |
| `width`         | number    | The preferred width of the bitmap in layout coordinates.                                            |
| `height`        | number    | The preferred height of the bitmap in layout coordinates.                                           |
| `linearScaling` | boolean   | If true, the bitmap will be scaled using linear filtering, otherwise nearest neighbor will be used. |

| Property        | Type    | Description                                                                                         |
|-----------------|---------|-----------------------------------------------------------------------------------------------------|
| `type`          | `tile`  | The bitmap will be tiled to fill the rectangle.                                                     |
| `width`         | number  | The preferred width of the bitmap in layout coordinates.                                            |
| `height`        | number  | The preferred height of the bitmap in layout coordinates.                                           |
| `linearScaling` | boolean | If true, the bitmap will be scaled using linear filtering, otherwise nearest neighbor will be used. |

| Property                | Type        | Description                                                                                                        |
|-------------------------|-------------|--------------------------------------------------------------------------------------------------------------------|
| `type`                  | `nineSlice` | The bitmap will is cut into nine slices to avoid stretching the border in the wrong directions.                    |
| `width`                 | number      | The preferred width of the bitmap in layout coordinates.                                                           |
| `height`                | number      | The preferred height of the bitmap in layout coordinates.                                                          |
| `left`                  | number      | The width of the left border in the bitmap in pixels.                                                              |
| `top`                   | number      | The height of the top border in the bitmap in pixels.                                                              |
| `right`                 | number      | The width of the right border in the bitmap in pixels.                                                             |
| `bottom`                | number      | The height of the bottom border in the bitmap in pixels.                                                           |
| `stretchHorizontalFill` | boolean     | If true, areas of the bitmap that need to be horizontally fit to the rectangle will be stretched instead of tiled. |
| `stretchVerticalFill`   | boolean     | If true, areas of the bitmap that need to be vertically fit to the rectangle will be stretched instead of tiled.   |
| `linearScaling`         | boolean     | If true, the bitmap will be scaled using linear filtering, otherwise nearest neighbor will be used.                |

### animation metadata

| Property     | Type   | Description                                                                                                |
|--------------|--------|------------------------------------------------------------------------------------------------------------|
| `frameCount` | number | The number of frames in the bitmap. The frames are expected to be stacked vertically in the source bitmap. |

### loading screen

| Property       | Type                                            | Description                                                  |
|----------------|-------------------------------------------------|--------------------------------------------------------------|
| `performance`  | [element](#element)                             | Configures the memory/cpu usage indicators.                  |
| `progressBars` | [progress bars element](#progress-bars-element) | Configures the current loading progress bars.                |
| `startupLog`   | [element](#element)                             | Configures how the most recent log messages are shown.       |
| `mojangLogo`   | [element](#element)                             | Configures the large Mojang logo.                            |
| `decoration`   | dictionary of [element](#element)               | Allows custom decorative elements to be added to the screen. |

### progress bars element

Inherits all properties of [element](#element), and adds the following:

| Property   | Type   | Description                                                        |
|------------|--------|--------------------------------------------------------------------|
| `labelGap` | number | The gap in virtual pixels between a bars label and the bar itself. |
| `barGap`   | number | The gap in virtual pixels between a bar and the next label or bar. |

### element

| Property              | Type                          | Description                                                                                                                                                                          |
|-----------------------|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `visible`             | boolean                       | Defaults to true. Can be used to hide pre-defined elements.                                                                                                                          |
| `maintainAspectRatio` | boolean                       | Defaults to true. If only one dimension of the element is fully defined, the other dimension will be scaled from the implicit element size while maintaining its original aspect ratio. |
| `left`                | [style length](#style-length) | Defaults to undefined. Positions the left edge of the element relative to the layout box.                                                                                            |
| `top`                 | [style length](#style-length) | Defaults to undefined. Positions the top edge of the element relative to the layout box.                                                                                             |
| `right`               | [style length](#style-length) | Defaults to undefined. Positions the right edge of the element relative to the layout box.                                                                                           |
| `bottom`              | [style length](#style-length) | Defaults to undefined. Positions the bottom edge of the element relative to the layout box.                                                                                          |
| `centerHorizontally`  | boolean                       | Defaults to false. If true, the element will be centered horizontally and then offset from that using its `left` position.                                                           |
| `centerVertically`    | boolean                       | Defaults to false. If true, the element will be centered vertically and then offset from that using its `top` position.                                                              |
| `font`                | string                        | Defaults to `default`. Can be used to override the font used by an element.                                                                                                          |

### style length

Lengths of elements can be specified as follows:

- `null`, which can be used to override a value set in a base theme to undefined.
- A simple numeric value, which is interpreted as layout pixels.
- A string containing a number followed by the `rem` suffix to indicate a size relative to the font size.
- A string containing a number followed by `%` to indicate a value relative to the available width (or height, depending
  on context).
