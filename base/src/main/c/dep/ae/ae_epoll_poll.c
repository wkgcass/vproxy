#include "ae_epoll.c"
#include "ae_poll.c"

static int aeApiCreate(aeEventLoop *eventLoop) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        return aeApiCreate_poll(eventLoop);
    } else {
        return aeApiCreate_epoll(eventLoop);
    }
}

static int aeApiResize(aeEventLoop *eventLoop, int setsize) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        return aeApiResize_poll(eventLoop, setsize);
    } else {
        return aeApiResize_epoll(eventLoop, setsize);
    }
}

static void aeApiFree(aeEventLoop *eventLoop) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        aeApiFree_poll(eventLoop);
    } else {
        aeApiFree_epoll(eventLoop);
    }
}

static int aeApiAddEvent(aeEventLoop *eventLoop, int fd, int mask) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        return aeApiAddEvent_poll(eventLoop, fd, mask);
    } else {
        return aeApiAddEvent_epoll(eventLoop, fd, mask);
    }
}

static void aeApiDelEvent(aeEventLoop *eventLoop, int fd, int mask) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        aeApiDelEvent_poll(eventLoop, fd, mask);
    } else {
        aeApiDelEvent_epoll(eventLoop, fd, mask);
    }
}

static int aeApiPoll(aeEventLoop *eventLoop, struct timeval *tvp) {
    if (eventLoop->flags & AE_FLAG_PREFER_POLL) {
        return aeApiPoll_poll(eventLoop, tvp);
    } else {
        return aeApiPoll_epoll(eventLoop, tvp);
    }
}

static char *aeApiName(void) {
    return "epoll_poll";
}
