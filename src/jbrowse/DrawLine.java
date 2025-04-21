package jbrowse;

import java.awt.*;

public class DrawLine implements IDrawCommand {
    private final int x1;
    private final int y1;
    private final int x2;
    private final int y2;
    private final Color color;
    private final int thickness;

    public DrawLine(int x1, int y1, int x2, int y2, Color color, int thickness) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.thickness = thickness;
    }


    @Override
    public void draw(Graphics2D g, int scroll) {
        g.setColor(color);
        g.setStroke(new BasicStroke(thickness));
        g.drawLine(x1, y1 - scroll, x2, y2 - scroll);
    }

    @Override
    public int getBottom() {
        return 0;
    }

    @Override
    public int getTop() {
        return 0;
    }
}
