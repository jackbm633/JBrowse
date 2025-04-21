package jbrowse;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout root - the document itself.
 */
public class DocumentLayout implements ILayoutNode {

    private static final int WIDTH = 800;
    private static final int HSTEP = 13;
    private static final int VSTEP = 16;
    private final INode node;
    private ILayoutNode parent;
    private final List<ILayoutNode> children;
    private final JPanel panel;
    private ILayoutNode child;
    private int width;
    private int height;

    public DocumentLayout(INode node, JPanel panel) {
        this.node = node;
        this.parent = null;
        this.children = new ArrayList<>();
        this.panel = panel;
    }

    public void layout() {
        child = new BlockLayout(node, panel, this, null);
        children.add(child);
        width = WIDTH - 2 * HSTEP;
        child.layout();
        this.height = child.getHeight();
    }

    @Override
    public int getX() {
        return HSTEP;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getY() {
        return VSTEP;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public ILayoutNode getParent() {
        return parent;
    }

    @Override
    public void setParent(ILayoutNode parent) {
        this.parent = parent;
    }

    @Override
    public List<ILayoutNode> getChildren() {
        return this.children;
    }

    @Override
    public List<IDrawCommand> paint() {
        return new ArrayList<>(List.of());
    }

    @Override
    public INode getNode() {
        return node;
    }

    @Override
    public boolean shouldPaint() {
        return true;
    }

    public List<IDrawCommand> paintEffects(List<IDrawCommand> cmds)
    {
        cmds = paintVisualEffects(node, cmds, selfRect());
        return cmds;
    }

    private Rectangle selfRect() {
        return new Rectangle(0, 0, width, height);
    }

    public static List<IDrawCommand> paintVisualEffects(INode node, List<IDrawCommand> cmds, Rectangle rectangle) {
        double opacity = Double.parseDouble(node.getStyle().getOrDefault("opacity","1.0"));

        var returnList = new ArrayList<IDrawCommand>();
        returnList.add(new Opacity(opacity, cmds));
        return returnList;
    }


}
