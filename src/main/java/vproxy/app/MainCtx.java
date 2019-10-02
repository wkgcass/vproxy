package vproxy.app;

import java.util.*;

public class MainCtx {
    private final List<MainOp> ops = new LinkedList<>();
    private final Map<String, Object> storage = new HashMap<>();
    private final List<Todo> todoList = new LinkedList<>();

    private static class Todo {
        final MainOp op;
        final String[] args;

        private Todo(MainOp op, String[] args) {
            this.op = op;
            this.args = args;
        }
    }

    public void addOp(MainOp args) {
        this.ops.add(args);
    }

    public MainOp seekOp(String key) {
        return ops.stream().filter(foo -> foo.key().equals(key)).findFirst().orElse(null);
    }

    public void addTodo(MainOp op, String[] args) {
        assert op.argCount() == args.length;
        int checkExitCode = op.pre(this, args);
        if (checkExitCode != 0) {
            System.exit(checkExitCode);
            return;
        }
        todoList.add(new Todo(op, args));
    }

    public <T> T get(String key, T defaultValue) {
        if (storage.containsKey(key)) {
            //noinspection unchecked
            return (T) storage.get(key);
        } else {
            return defaultValue;
        }
    }

    public void set(String key, Object value) {
        storage.put(key, value);
    }

    public void executeAll() {
        todoList.stream().sorted(Comparator.comparingInt(a -> a.op.order())).forEach(foo -> {
            int exitCode = foo.op.execute(this, foo.args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        });
    }
}
