package vfd;

import vproxybase.util.Logger;
import vproxybase.util.Utils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class TraceInvocationHandler implements InvocationHandler {
    private final Object target;

    public TraceInvocationHandler(Object target) {
        this.target = target;
    }

    private void printObject(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
            return;
        }
        if (o.getClass().isArray()) {
            sb.append("[");
            {
                int len = Array.getLength(o);
                for (int i = 0; i < len; ++i) {
                    if (i != 0) {
                        sb.append(", ");
                    }
                    printObject(sb, Array.get(o, i));
                }
            }
            sb.append("]");
            return;
        }
        if (o instanceof ByteBuffer) {
            sb.append("Buf[");
            boolean printFull = true;
            {
                ByteBuffer buf = (ByteBuffer) o;
                int pos = buf.position();
                int len = buf.limit() - pos;
                if (len > 32) {
                    len = 32;
                    printFull = false;
                }
                byte[] holder = Utils.allocateByteArray(len);
                buf.get(holder);
                buf.position(pos);

                char[] cs = new char[holder.length];
                for (int i = 0; i < holder.length; i++) {
                    cs[i] = Logger.toPrintableChar(holder[i]);
                }
                sb.append(new String(cs));
            }
            if (printFull) {
                sb.append("]");
            } else {
                sb.append(")");
            }
            return;
        }
        sb.append(o);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String tid = Long.toHexString(Thread.currentThread().getId());
        String methodName = method.getName();
        StringBuilder sb = new StringBuilder();
        sb.append("[tid=0x").append(tid).append("]");

        sb.append(methodName);
        sb.append("(");
        if (args != null) {
            boolean isFirst = true;
            for (Object o : args) {
                if (isFirst)
                    isFirst = false;
                else
                    sb.append(", ");
                printObject(sb, o);
            }
        }
        sb.append(") = ");
        System.err.print(sb.toString());
        sb.delete(0, sb.length());

        Object res;
        try {
            res = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            sb.append("[tid=0x").append(tid).append("]");

            Throwable t = e.getCause();
            sb.append("(no-return) ");
            sb.append(t.getClass().getName());
            sb.append(": ");
            sb.append(t.getMessage());
            System.err.println(sb);
            throw t;
        }

        sb.append("[tid=0x").append(tid).append("]");

        printObject(sb, res);
        sb.append(" (no-error)");
        if (methodName.equals("read") || methodName.equals("recvfromIPv4") || methodName.equals("recvfromIPv6")) {
            // need to print the read buf
            assert args != null;
            ByteBuffer buf = (ByteBuffer) args[1];
            sb.append(" buf=");
            printObject(sb, buf);
        }
        System.err.println(sb);

        return res;
    }
}
