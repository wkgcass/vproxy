#!/bin/bash

function setProxy()
{
IFS='
'
    port=`cat ~/vproxy-websocks-agent.conf | grep 'agent\.listen\ ' | tail -n 1 | awk '{print $2}'`
    nics=`networksetup -listallnetworkservices | tail +2`
    for nic in $nics
    do
        networksetup -setsocksfirewallproxy "$nic" 127.0.0.1 $port
        networksetup -setsocksfirewallproxystate "$nic" on
    done
}

function unsetProxy()
{
IFS='
'
    nics=`networksetup -listallnetworkservices`
    for nic in $nics
    do
        networksetup -setsocksfirewallproxystate "$nic" off
    done
}

function checkAndSetProxy()
{
    sleep 1s # sleep for 1 second to check whether the process is running
    cnt=`ps aux | grep 'vproxy\-.*\-WebSocksAgent-macos' | wc -l`
    if [[ "$cnt" -eq "0" ]]
    then
        ## the process failed to start
        return 1
    fi
    ## let's set the proxy
    setProxy
}

## stop the previous process first
pkill 'vproxy\-.*\-WebSocksAgent-macos'
## to current directory
cd "$(dirname "$0")"
## launch and save log to home directory, and unset when the process stops
executable=`ls | grep 'vproxy\-.*\-WebSocksAgent-macos$'`
("./$executable" || unsetProxy) >> ~/vproxy-websocks-agent.log 2>&1 &
## try to set proxy
checkAndSetProxy
