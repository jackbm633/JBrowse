package jbrowse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Text implements INode {

    private final String text; 
    private INode parent;
    private final List<INode> children = new ArrayList<>();
    private final Map<String, String> style = new HashMap<>();

    public Text(String text, INode parent) {
        this.text = text;
        this.parent = parent;
    }
    @Override
    public INode getParent() {
        return this.parent;
    }

    @Override
    public void setParent(INode parent) {
        this.parent = parent;
    }

    @Override
    public List<INode> getChildren() {
        return this.children;
    }

    @Override
    public Map<String, String> getStyle() {
        return style;
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public void setFocused(boolean focus) {

    }

    @Override
    public void setChildren(List<INode> newNodes) {
        children.clear();
        children.addAll(newNodes);
    }

    public String getText() {
        return text;
    }

    public String toString() {
        return text;
    }
}
