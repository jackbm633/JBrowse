package jbrowse;

import java.awt.*;
import java.awt.image.BufferedImage;

public record DrawText(int x1, int y1, String text, Font f, FontMetrics fm, Color c) implements IDrawCommand{
    @Override
    public void draw(Graphics2D g, int scroll) {
        g.setColor(c);
        g.setFont(f);
        g.drawString(text, x1, y1 + fm.getAscent() - scroll);
    }

    @Override
    public int getBottom() {
        return y1 + fm.getHeight();
    }

    @Override
    public int getTop() {
        return y1;
    }

    @Override
    public Rectangle getRect() {
        Graphics2D bufferedGraphics = (Graphics2D) new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
        var rect = fm.getStringBounds(text, bufferedGraphics).getBounds();
        rect.setLocation(x1, y1);
        return rect;
    }


}
