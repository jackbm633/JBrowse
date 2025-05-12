package jbrowse;

import java.util.List;
import java.util.Map;


public interface INode {
    
    INode getParent();
    void setParent(INode parent);
    List<INode> getChildren();

    Map<String, String> getStyle();

    boolean isFocused();

    void setFocused(boolean focus);

    void setChildren(List<INode> newNodes);
}