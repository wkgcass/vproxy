package vproxy.base.util.display;

import vproxy.base.util.coll.Tree;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TreeBuilder {
    private int indentation = 6;
    private final Tree<String, String> tree = new Tree<>();

    public void setIndentation(int indentation) {
        this.indentation = indentation;
    }

    public BranchBuilder branch(String data) {
        return new BranchBuilder(tree.branch(data));
    }

    public static class BranchBuilder {
        private final Tree.Branch<String, String> branch;

        public BranchBuilder(Tree.Branch<String, String> branch) {
            this.branch = branch;
        }

        public BranchBuilder branch(String data) {
            return new BranchBuilder(branch.branch(data));
        }

        public void leaf(String data) {
            branch.leaf(data);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("o").append("\n");
        var indents = new LinkedList<ToStringHelper>();
        indents.add(new ToStringHelper(0));
        toStringBranches(sb, indents, tree.branches().iterator());
        return sb.toString();
    }

    private static class ToStringHelper {
        final int indent;
        boolean show = true;

        private ToStringHelper(int indent) {
            this.indent = indent;
        }
    }

    private void toStringBranches(StringBuilder sb, LinkedList<ToStringHelper> helpers, Iterator<Tree.Branch<String, String>> branches) {
        while (branches.hasNext()) {
            var br = branches.next();
            appendIndent(sb, helpers);
            sb.append(br.data).append("\n");

            if (!branches.hasNext()) {
                helpers.getLast().show = false;
            }

            helpers.add(new ToStringHelper(helpers.getLast().indent + indentation));
            toStringBranches(sb, helpers, br.branches().iterator());
            toStringLeaves(sb, helpers, br.leaves().iterator());
            helpers.removeLast();
        }
    }

    private void toStringLeaves(StringBuilder sb, LinkedList<ToStringHelper> helpers, Iterator<Tree.Leaf<String, String>> leaves) {
        while (leaves.hasNext()) {
            var leaf = leaves.next();
            appendIndent(sb, helpers);
            sb.append(leaf.data);
            sb.append("\n");
        }
    }

    private void appendIndent(StringBuilder sb, List<ToStringHelper> helpers) {
        appendIndent0(sb, helpers);
        sb.append("\n");
        appendIndent0(sb, helpers);
        sb.delete(sb.length() - 1, sb.length());
        sb.append("+");
        sb.append("-".repeat(indentation - 3));
        sb.append("> ");
    }

    private void appendIndent0(StringBuilder sb, List<ToStringHelper> helpers) {
        int lastIndent = -1;
        for (var iterator = helpers.iterator(); iterator.hasNext(); ) {
            ToStringHelper h = iterator.next();
            if (lastIndent != -1) {
                sb.append(" ".repeat(h.indent - lastIndent - 1));
            }
            lastIndent = h.indent;
            if (h.show || !iterator.hasNext()) {
                sb.append("|");
            } else {
                sb.append(" ");
            }
        }
    }
}
