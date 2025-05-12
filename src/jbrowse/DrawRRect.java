package jbrowse;

import java.awt.*;

public record DrawRRect(Rectangle r, int radius, Color color)  implements  IDrawCommand{
    @Override
    public void draw(Graphics2D g, int scroll) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color oldColor = g.getColor();
        g.setColor(color);
        g.fillRoundRect(r.x, r.y - scroll, r.width, r.height, radius, radius);
        g.setColor(oldColor);
    }

    @Override
    public int getBottom() {
        return r.y + r.height;
    }

    @Override
    public int getTop() {
        return r.y;
    }

    @Override
    public Rectangle getRect() {
        return r;
    }
}
