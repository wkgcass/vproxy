#include <sys/poll.h>
#include <string.h>

typedef struct aeApiState_poll {
    struct pollfd* fds;
    int cap;
    int size;
} aeApiState_poll;

static int aeApiCreate_poll(aeEventLoop *eventLoop) {
    aeApiState_poll *state = zmalloc(sizeof(aeApiState_poll));

    if (!state) return -1;
    memset(state, 0, sizeof(aeApiState_poll));

    struct pollfd* fds = zmalloc(sizeof(struct pollfd) * eventLoop->setsize);
    if (fds == NULL) {
        return -1;
    }
    memset(fds, 0, sizeof(struct pollfd) * eventLoop->setsize);

    state->fds = fds;
    state->cap = eventLoop->setsize;

    eventLoop->apidata = state;
    return 0;
}

static int aeApiResize_poll(aeEventLoop *eventLoop, int setsize) {
     aeApiState_poll* state = eventLoop->apidata;

    if (state->cap == setsize) {
        return 0;
    }
    if (state->size > setsize) {
        return -1;
    }

    struct pollfd* fds = zmalloc(sizeof(struct pollfd) * setsize);
    if (fds == NULL) return -1;
    memset(fds, 0, sizeof(struct pollfd) * setsize);

    memcpy(fds, state->fds, setsize * sizeof(struct pollfd));

    struct pollfd* old = state->fds;
    state->fds = fds;
    state->cap = setsize;
    zfree(old);

    return 0;
}

static void aeApiFree_poll(aeEventLoop *eventLoop) {
    aeApiState_poll* state = eventLoop->apidata;
    zfree(state->fds);
    zfree(state);
}

static int aeApiAddEvent_poll(aeEventLoop *eventLoop, int fd, int mask) {
    aeApiState_poll *state = eventLoop->apidata;

    if (state->size == state->cap) return -1;
    struct pollfd* e = &state->fds[state->size];

    state->size += 1;
    e->fd = fd;
    e->events = 0;

    if (mask & AE_READABLE) e->events |= POLLIN;
    if (mask & AE_WRITABLE) e->events |= POLLOUT;
    return 0;
}

static void aeApiDelEvent_poll(aeEventLoop *eventLoop, int fd, int mask) {
    aeApiState_poll *state = eventLoop->apidata;

    for (int i = 0; i < state->size; ++i) {
        struct pollfd* e = &state->fds[i];
        if (e->fd == fd) {
            for (int j = i + 1; j < state->size; ++j) {
                state->fds[j-1] = state->fds[j];
            }
            --state->size;
            break;
        }
    }
}

static int aeApiPoll_poll(aeEventLoop *eventLoop, struct timeval *tvp) {
    aeApiState_poll *state = eventLoop->apidata;
    int retval, numevents = 0;

    retval = poll(state->fds,state->size,
            tvp ? (tvp->tv_sec*1000 + tvp->tv_usec/1000) : -1);
    if (retval > 0) {
        int j;

        numevents = 0;
        for (j = 0; j < state->size; j++) {
            int mask = 0;
            struct pollfd *e = state->fds+j;

            if (e->revents & POLLIN)  mask |= AE_READABLE;
            if (e->revents & POLLOUT) mask |= AE_WRITABLE;
            if (e->revents & POLLERR) mask |= AE_WRITABLE;
            if (e->revents & POLLHUP) mask |= AE_WRITABLE;
            eventLoop->fired[numevents].fd = e->fd;
            eventLoop->fired[numevents].mask = mask;
            ++numevents;
        }
    }
    return numevents;
}

static char *aeApiName_poll(void) {
    return "poll";
}
