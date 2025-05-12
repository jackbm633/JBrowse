package jbrowse;

import java.awt.*;

public interface IDrawCommand {
    void draw(Graphics2D g, int scroll);

    int getBottom();

    int getTop();

    Rectangle getRect();
}
