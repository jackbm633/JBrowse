package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jbrowse.Tab.getFont;
import static jbrowse.Tab.getFontMetricsFromCache;

public class TextLayout implements ILayoutNode {

    private final INode node;
    private final ILayoutNode parent;
    private final List<ILayoutNode> children;
    private final ILayoutNode previous;
    private final String word;
    private int width;
    private int x;
    private int y;
    private Font font;
    private int height;

    public FontMetrics getFontMetrics() {
        return fontMetrics;
    }

    private FontMetrics fontMetrics;

    public TextLayout(INode node, ILayoutNode parent, String word, ILayoutNode previous) {
        this.node = node;
        this.parent = parent;
        this.previous = previous;
        this.word = word;
        this.children = new ArrayList<>();
    }

    @Override
    public void layout() {
        int weight = getFontWeight(node.getStyle().get("font-weight"));
        int style = getFontStyle(node.getStyle().get("font-style")) | weight;
        double size = Float.parseFloat(node.getStyle().get("font-size").replace("px", ""));

        font = getFont((int) size, style);
        fontMetrics = getFontMetricsFromCache(font);
        
        this.width = fontMetrics.stringWidth(word);
        this.height = fontMetrics.getHeight();

        if (this.previous != null && this.previous instanceof TextLayout tl) {
            int spaceWidth = tl.getFontMetrics().stringWidth(" ");
            x = this.previous.getX() + spaceWidth + this.previous.getWidth();
        } else {
            x = this.parent.getX();
        }
    }

    private int getFontStyle(String s) {
        if (Objects.equals(s, "italic")) {
            return Font.ITALIC;
        }
        return Font.PLAIN;
    }

    private int getFontWeight(String s) {
        if (Objects.equals(s, "normal") || "400".equals(s)) {
            return Font.PLAIN;
        } else if ("bold".equals(s) || "700".equals(s)) {
            return Font.BOLD;
        }
        return Font.PLAIN;
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

    }

    @Override
    public List<ILayoutNode> getChildren() {
        return children;
    }

    @Override
    public List<IDrawCommand> paint() {
        org.silentsoft.csscolor4j.Color c = org.silentsoft.csscolor4j.Color.valueOf(node.getStyle().get("color"));
        Color color = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(c.getOpacity() * 255));
        List<IDrawCommand> iDrawCommands = new ArrayList<>();
        iDrawCommands.add(new DrawText(x, y, word, font, fontMetrics, color));
        return iDrawCommands;
    }

    @Override
    public INode getNode() {
        return node;
    }

    @Override
    public boolean shouldPaint() {
        return true;
    }

    public void setY(int y) {
        this.y = y;
    }

    public List<IDrawCommand> paintEffects(List<IDrawCommand> cmds)
    {
        cmds = paintVisualEffects(node, cmds, selfRect());
        return cmds;
    }

    private Rectangle selfRect() {
        return new Rectangle(x, y, width, height);
    }

    public static List<IDrawCommand> paintVisualEffects(INode node, List<IDrawCommand> cmds, Rectangle rectangle) {
        return BlockLayout.paintVisualEffects(node, cmds, rectangle);

    }
}
