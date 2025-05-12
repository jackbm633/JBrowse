package jbrowse;

import java.util.Objects;

public class TagSelector implements ISelector {

    private final String tag;
    public TagSelector(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean matches(INode node) {
        return node instanceof Element e && Objects.equals(tag, e.getTag());
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
