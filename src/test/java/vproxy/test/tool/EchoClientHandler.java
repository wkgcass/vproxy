package vproxy.test.tool;

import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class EchoClientHandler implements Handler<SocketChannel> {
    private final RingBuffer buffer = RingBuffer.allocateDirect(8); // let's set this very small, to test all the code flow

    @Override
    public void accept(HandlerContext<SocketChannel> ctx) {
        // should not fire
    }

    @Override
    public void connected(HandlerContext<SocketChannel> ctx) {
        // should not fire
    }

    @Override
    public void readable(HandlerContext<SocketChannel> ctx) {
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
                ctx.modify((ctx.getOps() & ~SelectionKey.OP_READ) | SelectionKey.OP_WRITE);
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
            ctx.modify(ctx.getOps() | SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void writable(HandlerContext<SocketChannel> ctx) {
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
        int oldOps = ctx.getOps();
        int ops = oldOps;
        if (writeBytes > 0) {
            // buffer definitely has some space left now
            if ((ops & SelectionKey.OP_READ) == 0) {
                System.out.println("\033[0;32mbuffer now has some free space, let's start reading\033[0m");
            }
            ops |= SelectionKey.OP_READ;
        }
        if (buffer.used() == 0) {
            // nothing to write anymore
            ops &= ~SelectionKey.OP_WRITE;
            System.out.println("\033[0;32mnothing to write for now, let's stop writing\033[0m");
        }
        if (oldOps != ops) {
            ctx.modify(ops);
        }
    }

    @Override
    public void removed(HandlerContext<SocketChannel> ctx) {
        // close the connection here
        try {
            ctx.getChannel().close();
        } catch (IOException e) {
            // we can do nothing about it
            e.printStackTrace();
        }
    }
}
