package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.thread.VProxyThread;
import vproxybase.util.promise.Promise;

import static org.junit.Assert.assertEquals;

public class TestPromise {
    private SelectorEventLoop loop;

    @Before
    public void setUp() throws Exception {
        loop = SelectorEventLoop.open();
        loop.loop(r -> VProxyThread.create(r, "test-promise-loop"));
    }

    @After
    public void tearDown() throws Exception {
        loop.close();
    }

    @Test
    public void simpleSuccess() throws Throwable {
        int n = Promise.resolve(123).then(i -> Promise.resolve(i + 15)).then(i -> Promise.resolve(i - 10)).then(i -> Promise.resolve(i * 2)).block();
        assertEquals((123 + 15 - 10) * 2, n);
    }

    @Test
    public void simpleFail() {
        try {
            Promise.resolve(123).then(i -> Promise.resolve(i + 15)).<Integer>then(i -> {
                throw new Exception("a");
            }).then(i -> Promise.resolve(i - 10)).block();
        } catch (Throwable throwable) {
            assertEquals("a", throwable.getMessage());
        }
    }

    @Test
    public void simpleFail2() {
        try {
            Promise.resolve(123).then(i -> Promise.resolve(i + 15))
                .<Integer>then(i -> Promise.reject(new Exception("a")))
                .then(i -> Promise.resolve(i - 10)).block();
        } catch (Throwable throwable) {
            assertEquals("a", throwable.getMessage());
        }
    }

    @Test
    public void simpleCatch() throws Throwable {
        int n = Promise.resolve(123).then(i -> Promise.resolve(i + 15)).<Integer>then(i -> {
            assertEquals(123 + 15, i.intValue());
            throw new Exception("a");
        }).exception(err -> {
            assertEquals("a", err.getMessage());
            return Promise.resolve(456);
        }).then(i -> Promise.resolve(i - 10)).block();
        assertEquals(456 - 10, n);
    }

    @Test
    public void simpleCatch2() throws Throwable {
        int n = Promise.resolve(123).then(i -> Promise.resolve(i + 15)).<Integer>then(i -> {
            assertEquals(123 + 15, i.intValue());
            throw new Exception("a");
        }).then(i -> Promise.resolve(i - 10)).exception(err -> {
            assertEquals("a", err.getMessage());
            return Promise.resolve(456);
        }).then(i -> Promise.resolve(i - 10)).block();
        assertEquals(456 - 10, n);
    }

    @Test
    public void asyncSuccess() throws Throwable {
        int n = new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(123)))
            .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i + 15))))
            .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i - 10))))
            .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i * 2))))
            .block();
        assertEquals((123 + 15 - 10) * 2, n);
    }

    @Test
    public void asyncFail() {
        try {
            new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(123)))
                .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i + 15))))
                .<Integer>then(i -> {
                    assertEquals(123 + 15, i.intValue());
                    throw new Exception("a");
                })
                .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i - 10))))
                .block();
        } catch (Throwable throwable) {
            assertEquals("a", throwable.getMessage());
        }
    }

    @Test
    public void asyncFail2() {
        try {
            new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(123)))
                .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i + 15))))
                .<Integer>then(i -> new Promise<>((resolve, reject) -> {
                    assertEquals(123 + 15, i.intValue());
                    loop.delay(50, () -> reject.accept(new Exception("a")));
                }))
                .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i - 10))))
                .block();
        } catch (Throwable throwable) {
            assertEquals("a", throwable.getMessage());
        }
    }

    @Test
    public void asyncCatch() throws Throwable {
        int n = new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(123)))
            .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i + 15))))
            .<Integer>then(i -> {
                assertEquals(123 + 15, i.intValue());
                throw new Exception("a");
            }).exception(err -> {
                assertEquals("a", err.getMessage());
                return new Promise<>((resolve, reject) -> loop.delay(50, () -> resolve.accept(456)));
            }).then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i - 10))))
            .block();
        assertEquals(456 - 10, n);
    }

    @Test
    public void asyncCatch2() throws Throwable {
        int n = new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(123)))
            .then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i + 15))))
            .then(i -> new Promise<Integer>((resolve, reject) -> {
                assertEquals(123 + 15, i.intValue());
                loop.delay(50, () -> resolve.accept(i - 10));
            }))
            .<Integer>then(i -> {
                throw new Exception("a");
            }).exception(err -> {
                assertEquals("a", err.getMessage());
                return new Promise<>((resolve, reject) -> loop.delay(50, () -> resolve.accept(456)));
            }).then(i -> new Promise<Integer>((resolve, reject) -> loop.delay(50, () -> resolve.accept(i - 10))))
            .block();
        assertEquals(456 - 10, n);
    }
}
