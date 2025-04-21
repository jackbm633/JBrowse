package jbrowse;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Represents a graphical composition with a specified opacity level that
 * applies to all of its child drawing commands. Delegates the rendering
 * of its children to an offscreen image and renders the composited image
 * with the specified opacity.
 */
public class Opacity implements IDrawCommand {
    private final double opacity;
    private final List<IDrawCommand> children;
    private final Rectangle rect = new Rectangle();

    public Opacity(double opacity, @NotNull List<IDrawCommand> children) {
        this.opacity = opacity;
        this.children = children;
        for (IDrawCommand cmd : children) {
            rect.add(cmd.getRect());
        }
    }

    /**
     * Executes the drawing operation for the current opacity context using a specified Graphics2D canvas.
     * Renders the children commands to an offscreen image with configured opacity and finally draws it
     * onto the provided canvas.
     *
     * @param canvas the Graphics2D canvas on which the drawing operation will be performed
     */
    public void execute(Graphics2D canvas) {
        if (rect.width == 0 || rect.height == 0 || opacity >= 1)
        {
            for (IDrawCommand cmd : children) {
                cmd.draw(canvas, 0);
            }
            return;
        }

        BufferedImage offscreenImage = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = offscreenImage.createGraphics();

        graphics2D.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Apply coordinate translation to the graphics context so that we can draw the image
        graphics2D.translate(-rect.x, -rect.y);

        for (IDrawCommand cmd : children) {
            cmd.draw(graphics2D, 0);
        }

        graphics2D.dispose();

        // Now, draw the image onto the canvas with the correct opacity.
        AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity);
        canvas.setComposite(ac);
        canvas.drawImage(offscreenImage, rect.x, rect.y, null);
        // Reset the composite to what it was before.
        canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
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