function FindProxyForURL(url, host) {
    if (host === 'www.163.com') {
        return 'a string which is not "DIRECT"'
    }
    return "DIRECT";
}

// additionally, if this script throws an exception
// the result will be considered as `DIRECT`
