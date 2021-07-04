package vproxy.base.util;

import java.util.ArrayList;
import java.util.List;

public class IntRanges implements Iterable<Integer> {
    private final int[][] ranges;
    public final int length;

    public IntRanges(String exp) {
        if (exp.isBlank()) {
            throw new IllegalArgumentException("input expression is empty");
        }
        exp = exp.trim();
        String[] ranges = exp.split(",");

        int lastMax = Integer.MIN_VALUE;
        List<int[]> lsRanges = new ArrayList<>();
        for (String s : ranges) {
            s = s.trim();

            if (Utils.isInteger(s)) {
                int n = Integer.parseInt(s);
                checkMax(lastMax, n, s);
                lastMax = n;

                lsRanges.add(new int[]{n, n});
                continue;
            }

            String[] nums = s.split("-");
            if (nums.length != 2) {
                throw new IllegalArgumentException("invalid expression: " + s);
            }
            nums[0] = nums[0].trim();
            nums[1] = nums[1].trim();

            if (!Utils.isInteger(nums[0])) {
                throw new IllegalArgumentException(nums[0] + " is not an integer in " + s);
            }
            if (!Utils.isInteger(nums[1])) {
                throw new IllegalArgumentException(nums[1] + " is not an integer in " + s);
            }
            int a = Integer.parseInt(nums[0]);
            int b = Integer.parseInt(nums[1]);

            checkMax(lastMax, a, s);
            if (a > b) {
                throw new IllegalArgumentException(a + " > " + b);
            }

            lsRanges.add(new int[]{a, b});
        }

        int[][] result = new int[lsRanges.size()][];
        for (int i = 0; i < result.length; ++i) {
            result[i] = lsRanges.get(i);
        }
        this.ranges = result;

        int cnt = 0;
        for (int[] r : result) {
            cnt += (r[1] - r[0] + 1);
        }
        this.length = cnt;
    }

    private void checkMax(int lastMax, int n, String exp) {
        if (n <= lastMax) {
            throw new IllegalArgumentException(n + " <= " + lastMax + " in " + exp);
        }
    }

    @Override
    public Iterator iterator() {
        return new Iterator();
    }

    public class Iterator implements java.util.Iterator<Integer> {
        private int cursor = 0;
        private int last = Integer.MIN_VALUE;

        @Override
        public boolean hasNext() {
            return cursor < ranges.length;
        }

        @Override
        public Integer next() {
            return nextInt();
        }

        public int nextInt() {
            int[] range = ranges[cursor];
            int result;
            if (last == Integer.MIN_VALUE) {
                result = range[0];
                last = result;
            } else {
                result = ++last;
            }
            if (last == range[1]) {
                ++cursor;
                last = Integer.MIN_VALUE;
            }
            return result;
        }

        public void reset() {
            cursor = 0;
            last = Integer.MIN_VALUE;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (int[] range : ranges) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            if (range[0] == range[1]) {
                sb.append(range[0]);
            } else {
                sb.append(range[0]).append("-").append(range[1]);
            }
        }
        return sb.toString();
    }
}
