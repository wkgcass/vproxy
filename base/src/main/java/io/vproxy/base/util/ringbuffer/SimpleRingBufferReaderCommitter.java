package io.vproxy.base.util.ringbuffer;

import io.vproxy.pni.PanamaUtils;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class SimpleRingBufferReaderCommitter {
    private final SimpleRingBuffer buffer;
    private final MemorySegment mem;

    private final List<Todo> todos = new ArrayList<>();

    public SimpleRingBufferReaderCommitter(SimpleRingBuffer buffer) {
        this.buffer = buffer;
        this.mem = PanamaUtils.format(buffer.getBuffer().realBuffer());
        checkBufferState();
    }

    @SuppressWarnings("RedundantIfStatement") // the redundant makes code more clear
    public boolean check() {
        if (todos.isEmpty()) {
            if (buffer.getSPos() != 0) {
                return false;
            }
            return true;
        }
        var first = todos.get(0);
        if (buffer.getSPos() != first.sPos) {
            return false;
        }
        return true;
    }

    private void checkBufferState() {
        if (!check()) {
            throw new IllegalStateException("the buffer " + buffer + " is read outside of this committer " + this);
        }
    }

    public MemorySegment[] read() {
        checkBufferState();
        if (buffer.used() == 0) return new MemorySegment[0];
        if (!todos.isEmpty() && todos.getLast().ePos == buffer.getEPos()) return new MemorySegment[0];

        if (todos.isEmpty()) {
            var s = 0;
            var e = buffer.getEPos();
            var len = e - s;
            if (e == buffer.getCap()) e = 0;

            var seg = mem.asSlice(0, len);
            todos.add(new Todo(s, e, len, seg));
            return new MemorySegment[]{seg};
        }

        var last = todos.getLast();
        if (buffer.getEPosIsAfterSPos()) {
            // if the buffer is in e-after-s state
            // then the todos array would not contain elements that are added when ring buffer is in e-before-s state
            // so the ePos in todos list will not > buffer current ePos
            //
            // When the ringbuffer is in e-before-s state:
            //        e       s            cap    (e might == s in this state)
            // +------+-------+------------+
            // | data |       |  data      |
            // +------+-------+------------+
            // in this state, data will be read from the buffer from s to cap, and from 0 to e
            // resulting in 2 elements in todos list
            //
            // If the ringbuffer wants to change to e-after-s state,
            // the s-to-cap part must be committed.
            // And after committing the s-to-cap part,
            // the elements left in the todos list would only contain elements from 0 to e

            var s = last.ePos;
            var e = buffer.getEPos();
            var len = e - s;
            if (e == buffer.getCap()) e = 0;

            var seg = mem.asSlice(s, len);
            todos.add(new Todo(s, e, len, seg));
            return new MemorySegment[]{seg};
        }

        int retLen = 1;
        if (buffer.getEPos() > 0 && last.ePos > buffer.getEPos()) {
            ++retLen;
        }
        var ret = new MemorySegment[retLen];
        int retIdx = 0;
        if (last.ePos > buffer.getEPos()) {
            var s = last.ePos;
            var e = 0;
            var len = buffer.getCap() - s;

            var seg = mem.asSlice(s, len);
            last = new Todo(s, e, len, seg);
            todos.add(last);
            ret[retIdx++] = seg;
        }
        if (buffer.getEPos() > 0) {
            var s = last.ePos;
            var e = buffer.getEPos();
            var len = e - s;

            var seg = mem.asSlice(s, len);
            todos.add(new Todo(s, e, len, seg));
            ret[retIdx] = seg;
        }
        return ret;
    }

    public void commit(MemorySegment seg) {
        checkBufferState();
        if (todos.isEmpty()) {
            throw new NoSuchElementException(String.valueOf(seg));
        }
        var first = todos.getFirst();
        Todo found = null;
        for (var todo : todos) {
            if (todo.seg.address() == seg.address()) {
                todo.committed = true;
                found = todo;
                break;
            }
        }
        if (found == null) {
            throw new NoSuchElementException(String.valueOf(seg));
        }
        if (first != found) {
            return;
        }
        // clear elements until reaches first uncommitted
        for (var ite = todos.iterator(); ite.hasNext(); ) {
            var todo = ite.next();
            if (!todo.committed) {
                break;
            }
            ite.remove();
        }
        // need to modify the ring buffer
        try {
            buffer.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                buffer -> {
                    if (todos.isEmpty()) { // no todos, just consume everything
                        buffer.position(buffer.limit());
                        return;
                    }
                    var f = todos.getFirst();
                    if (f.sPos < buffer.position()) { // need to reach the cap
                        buffer.position(buffer.limit());
                    } else {
                        buffer.position(f.sPos);
                    }
                });
        } catch (IOException e) {
            // it's memory operation, should not happen
            throw new RuntimeException(e);
        }
    }

    public boolean isIdle() {
        return todos.isEmpty();
    }

    private static class Todo {
        final int sPos;
        final int ePos;
        final int len;
        final MemorySegment seg;
        boolean committed = false;

        private Todo(int sPos, int ePos, int len, MemorySegment seg) {
            this.sPos = sPos;
            this.ePos = ePos;
            this.len = len;
            this.seg = seg;
        }

        @Override
        public String toString() {
            return "{" +
                "sPos=" + sPos +
                ", ePos=" + ePos +
                ", len=" + len +
                '}';
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("SimpleRingBufferReaderCommitter[");
        var isFirst = true;
        for (var todo : todos) {
            if (todo.committed) {
                continue;
            }
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(todo);
        }
        sb.append("]");
        return sb.toString();
    }
}
