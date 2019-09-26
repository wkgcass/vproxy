package vproxy.util;

import java.util.Iterator;
import java.util.LinkedList;

public class Tree<BRANCH, LEAF> {
    private final LinkedList<Branch<BRANCH, LEAF>> branches = new LinkedList<>();
    private final LinkedList<Leaf<BRANCH, LEAF>> leaves = new LinkedList<>();

    public static class Branch<BRANCH, LEAF> extends Tree<BRANCH, LEAF> {
        public final Tree<BRANCH, LEAF> parent;
        public final BRANCH data;

        Branch(Tree<BRANCH, LEAF> parent, BRANCH data) {
            this.parent = parent;
            this.data = data;
        }
    }

    public static class Leaf<BRANCH, LEAF> {
        public final Tree<BRANCH, LEAF> parent;
        public final LEAF data;

        Leaf(Tree<BRANCH, LEAF> parent, LEAF data) {
            this.parent = parent;
            this.data = data;
        }
    }

    public Branch<BRANCH, LEAF> branch(BRANCH data) {
        var br = new Branch<>(this, data);
        branches.add(br);
        return br;
    }

    public Leaf<BRANCH, LEAF> leaf(LEAF data) {
        var l = new Leaf<>(this, data);
        leaves.add(l);
        return l;
    }

    public Branch<BRANCH, LEAF> lastBranch() {
        if (branches.isEmpty())
            return null;
        return branches.getLast();
    }

    public Iterable<Branch<BRANCH, LEAF>> branches() {
        return () -> {
            var ite = branches.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return ite.hasNext();
                }

                @Override
                public Branch<BRANCH, LEAF> next() {
                    return ite.next();
                }
            };
        };
    }

    public Iterable<Leaf<BRANCH, LEAF>> leaves() {
        return () -> {
            var ite = leaves.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return ite.hasNext();
                }

                @Override
                public Leaf<BRANCH, LEAF> next() {
                    return ite.next();
                }
            };
        };
    }

    public Iterable<LEAF> leafData() {
        return () -> {
            var ite = leaves.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return ite.hasNext();
                }

                @Override
                public LEAF next() {
                    return ite.next().data;
                }
            };
        };
    }
}
