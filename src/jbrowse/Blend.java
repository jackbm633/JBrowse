
package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.VolatileImage;
import java.util.List;

/**
 * Represents a graphical composition with a specified opacity level that
 * applies to all of its child drawing commands. Delegates the rendering
 * of its children to an offscreen image and renders the composited image
 * with the specified opacity. Uses GPU acceleration when available.
 */
public class Blend implements IDrawCommand {
    private final List<IDrawCommand> children;
    private Rectangle rect;
    private final Composite composite;
    private GraphicsConfiguration graphicsConfig;

    public Blend(Composite composite, @NotNull List<IDrawCommand> children) {
        this.composite = composite;
        this.children = children;
        // Get the default graphics configuration for the main screen
        this.graphicsConfig = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();

        for (IDrawCommand cmd : children) {
            if (rect == null) {
                rect = cmd.getRect();
            } else {
                var rct = cmd.getRect();
                if (rct != null) {
                    rect.add(rct);
                }
            }
        }
    }

    /**
     * Executes the drawing operation for the current opacity context using a specified Graphics2D canvas.
     * Renders the children commands to a GPU-accelerated offscreen image with configured opacity and
     * finally draws it onto the provided canvas.
     *
     * @param canvas the Graphics2D canvas on which the drawing operation will be performed
     */
    public void execute(Graphics2D canvas) {
        if (rect == null || rect.width == 0 || rect.height == 0 ||
                (composite instanceof AlphaComposite ac && ac.getAlpha() >= 1.0 &&
                        ac.getRule() != AlphaComposite.DST_IN)) {
            for (IDrawCommand cmd : children) {
                cmd.draw(canvas, 0);
            }
            return;
        }

        // Create a volatile image for GPU-accelerated rendering
        VolatileImage volatileImage = createVolatileImage(rect.width, rect.height);

        do {
            // Check if we need to recreate the volatile image
            if (volatileImage.validate(graphicsConfig) == VolatileImage.IMAGE_INCOMPATIBLE) {
                volatileImage = createVolatileImage(rect.width, rect.height);
            }

            Graphics2D graphics2D = volatileImage.createGraphics();
            try {
                // Clear the background to ensure transparency
                graphics2D.setComposite(AlphaComposite.Clear);
                graphics2D.fillRect(0, 0, rect.width, rect.height);
                graphics2D.setComposite(AlphaComposite.SrcOver);

                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Apply coordinate translation
                graphics2D.translate(-rect.x, -rect.y);

                // Draw all child commands
                for (IDrawCommand cmd : children) {
                    cmd.draw(graphics2D, 0);
                }
            } finally {
                graphics2D.dispose();
            }

            // Draw the volatile image to the canvas with the specified composite
            Composite originalComposite = canvas.getComposite();
            canvas.setComposite(composite);
            canvas.drawImage(volatileImage, rect.x, rect.y, null);
            canvas.setComposite(originalComposite);

        } while (volatileImage.contentsLost());

        // Clean up
        volatileImage.flush();
    }

    private VolatileImage createVolatileImage(int width, int height) {
        return graphicsConfig.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT);
    }

    @Override
    public void draw(Graphics2D g, int scroll) {
        execute(g);
    }

    @Override
    public int getBottom() {
        return rect.y + rect.height;
    }

    @Override
    public int getTop() {
        return rect.y;
    }

    @Override
    public Rectangle getRect() {
        return rect;
    }
}