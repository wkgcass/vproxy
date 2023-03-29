package io.vproxy.vswitch.node;

public enum HandleResult {
    // if returned in preHandle, the node is skipped and next node will be checked
    //   however if it's the last node, it will be picked up
    // if returned in handle, it has the same effect as PICK
    PASS,
    // if returned in preHandle, the node is skipped and next node will be checked
    // if returned in handle, it has the same effect as DROP
    CONTINUE,
    // if returned in preHandle, the node is picked up
    // if returned in handle, the node in pkb will be used as the next node
    //   if the pkb doesn't have the next node, the packet will be dropped
    PICK,
    // if returned in preHandle, the node is picked up
    // if returned in handle, no further actions will be done for the packet
    STOLEN,
    // if returned in preHandle, no further actions will be done for the packet
    // if returned in handle, no further actions will be done for the packet
    DROP,
}
