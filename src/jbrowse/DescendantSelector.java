package jbrowse;

public class DescendantSelector implements ISelector {
    private final ISelector ancestor;
    private final ISelector descendant;

    public DescendantSelector(ISelector ancestor, ISelector descendant) {
        this.ancestor = ancestor;
        this.descendant = descendant;
    }

    @Override
    public boolean matches(INode node) {
        if (!descendant.matches(node)) {
            return false;
        }
        while (node.getParent() != null) {
            if (ancestor.matches(node.getParent())) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    @Override
    public int getPriority() {
        return ancestor.getPriority() + descendant.getPriority();
    }
}
