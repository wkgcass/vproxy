package io.vproxy.test.tool;

import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.vfd.Event;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;

public class EchoClientHandler implements Handler<SocketFD> {
    private final RingBuffer buffer = RingBuffer.allocateDirect(8); // let's set this very small, to test all the code flow

    @Override
    public void accept(HandlerContext<SocketFD> ctx) {
        // should not fire
    }

    @Override
    public void connected(HandlerContext<SocketFD> ctx) {
        // should not fire
    }

    @Override
    public void readable(HandlerContext<SocketFD> ctx) {
        int readBytes;
        try {
            readBytes = buffer.storeBytesFrom(ctx.getChannel());
        } catch (IOException e) {
            // error occurred on the socket
            // remove from loop and close it
            ctx.remove();
            try {
                ctx.getChannel().close();
            } catch (IOException e1) {
                // we can do nothing about it, just ignore
            }
            return;
        }
        if (readBytes == 0) {
            // cannot read any data
            if (buffer.free() == 0) {
                // reached limit
                // remove read event and add write event
                ctx.modify(ctx.getOps().reduce(EventSet.read()).combine(EventSet.write()));
                // let's print this in std out, otherwise we cannot see the output being separated
                System.out.println("\033[0;36mbuffer is full, let's stop reading and start writing\033[0m");
            }
        } else if (readBytes < 0) {
            // remote write is closed
            // we just ignore the remote read and close the connection
            System.err.println("connection closed");
            ctx.remove();
            try {
                ctx.getChannel().close();
            } catch (IOException e) {
                // we can do nothing about it, just ignore
            }
        } else {
            // print to console
            System.out.println("buffer now looks like: " + buffer);
            // add write event
            ctx.modify(ctx.getOps().combine(EventSet.write()));
        }
    }

    @Override
    public void writable(HandlerContext<SocketFD> ctx) {
        final int writeBytes;
        try {
            // maxBytesToWrite is set to a very strange number 3
            // to demonstrate how it operates when buffer is almost full
            writeBytes = buffer.writeTo(ctx.getChannel(), 3);
            // you cloud simply use buffer.writeTo(ctx.getChannel())
        } catch (IOException e) {
            // error occurred
            // remove the channel
            ctx.remove();
            try {
                ctx.getChannel().close();
            } catch (IOException e1) {
                // we can do nothing about it
            }
            return;
        }
        EventSet oldOps = ctx.getOps();
        EventSet ops = oldOps;
        if (writeBytes > 0) {
            // buffer definitely has some space left now
            if (!ops.have(Event.READABLE)) {
                System.out.println("\033[0;32mbuffer now has some free space, let's start reading\033[0m");
            }
            ops = ops.combine(EventSet.read());
        }
        if (buffer.used() == 0) {
            // nothing to write anymore
            ops = ops.reduce(EventSet.write());
            System.out.println("\033[0;32mnothing to write for now, let's stop writing\033[0m");
        }
        if (!oldOps.equals(ops)) {
            ctx.modify(ops);
        }
    }

    @Override
    public void removed(HandlerContext<SocketFD> ctx) {
        // close the connection here
        try {
            ctx.getChannel().close();
        } catch (IOException e) {
            // we can do nothing about it
            e.printStackTrace();
        }
    }
}
