package jbrowse;

import java.awt.*;

public class DrawOutline implements IDrawCommand {
    private final Rectangle newtabRect;
    private final Color color;
    private final int thickness;

    public DrawOutline(Rectangle newtabRect, Color color, int thickness) {
        this.newtabRect = newtabRect;
        this.color = color;
        this.thickness = thickness;
    }

    @Override
    public void draw(Graphics2D g, int scroll) {
        g.setColor(color);
        g.setStroke(new BasicStroke(thickness));
        g.drawRect(newtabRect.x, newtabRect.y, newtabRect.width, newtabRect.height);
    }

    @Override
    public int getBottom() {
        return 0;
    }

    @Override
    public int getTop() {
        return 0;
    }

    @Override
    public Rectangle getRect() {
        return newtabRect;
    }
}
