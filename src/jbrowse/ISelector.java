package jbrowse;

public interface ISelector {
    boolean matches(INode node);
    int getPriority();
}
