package jbrowse;

import java.util.ArrayList;
import java.util.List;

public class LineLayout implements ILayoutNode {

    private final INode node;
    private ILayoutNode parent;
    private final List<ILayoutNode> children;
    private final ILayoutNode previous;
    private int width;
    private int x;
    private int y;
    private int height;

    public LineLayout(INode node, ILayoutNode parent, ILayoutNode previous) {
        this.node = node;
        this.parent = parent;
        this.previous = previous;
        this.children = new ArrayList<>();
    }

    @Override
    public void layout() {
        this.width = this.parent.getWidth();
        this.x = this.parent.getX();

        if (this.previous == null) {
            this.y = this.parent.getY();
        } else {
            this.y = this.previous.getY() + this.previous.getHeight();
        }

        for (ILayoutNode tl : children) {
            tl.layout();
        }


        int maxAscent = 0;
        for (ILayoutNode child : children) {
            if (child instanceof TextLayout) {
                int ascent = ((TextLayout) child).getFontMetrics().getAscent();
                if ( ascent > maxAscent) {
                    maxAscent = ascent;
                }
            } else if (child instanceof InputLayout il) {
                int ascent = il.getFontMetrics().getAscent();
                if ( ascent > maxAscent) {
                    maxAscent = ascent;
                }
            }
        }
        int baseline = (int) (y + 1.25 * maxAscent);
        for (ILayoutNode word : children) {
            if (word instanceof TextLayout wordLayout){
                wordLayout.setY(baseline - ((TextLayout) word).getFontMetrics().getAscent());
            } else if (word instanceof InputLayout il) {
                il.setY(baseline - il.getFontMetrics().getAscent());
            }

        }
        int maxDescent = 0;
        for (ILayoutNode child : children) {
            if (child instanceof TextLayout) {
                int descent = ((TextLayout) child).getFontMetrics().getDescent();
                if ( descent > maxDescent) {
                    maxDescent = descent;
                }
            } else if (child instanceof InputLayout il) {
                int descent = il.getFontMetrics().getDescent();
                if ( descent > maxDescent) {
                    maxDescent = descent;
                }
            }
        }
        height = (int) (1.25 * (maxAscent + maxDescent));
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getY() {
        return y;
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
        return children;
    }

    @Override
    public List<IDrawCommand> paint() {
        return List.of();
    }

    @Override
    public INode getNode() {
        return node;
    }

    @Override
    public boolean shouldPaint() {
        return true;
    }
}
