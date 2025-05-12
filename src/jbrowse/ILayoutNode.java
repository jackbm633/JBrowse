package jbrowse;

import java.util.List;

public interface ILayoutNode {
    void layout();

    int getX();

    int getWidth();

    int getY();

    int getHeight();

    ILayoutNode getParent();
    void setParent(ILayoutNode parent);
    List<ILayoutNode> getChildren();

    List<IDrawCommand> paint();

    List<IDrawCommand> paintEffects(List<IDrawCommand> paintList);
    INode getNode();

    boolean shouldPaint();
}
