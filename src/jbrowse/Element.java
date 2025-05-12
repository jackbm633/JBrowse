package jbrowse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Element implements INode {

    private final String tag;
    private final Map<String, String> attributes;
    private INode parent;
    private final List<INode> children = new ArrayList<>();
    private final Map<String, String> style = new HashMap<>();
    private boolean isFocused;

    public Element(String tag, Map<String, String> attributes, INode parent){
        this.tag = tag;
        this.parent = parent;
        this.attributes = attributes;
    }

    @Override
    public INode getParent() {
        return parent;
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

    public String getTag() {
        return tag;
    }
    
    public String toString()
    {
        return "<" + tag + ">";
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public boolean isFocused() {
        return isFocused;
    }

    public void setFocused(boolean focused) {
        isFocused = focused;
    }

    @Override
    public void setChildren(List<INode> newNodes) {
        children.clear();
        children.addAll(newNodes);
    }
}
 