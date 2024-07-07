package io.vproxy.base;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.graal.*;
import io.vproxy.r.org.graalvm.nativeimage.*;
import java.lang.foreign.*;
import java.nio.ByteBuffer;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.hosted.*;

public class NativeAccessGraalFeature implements org.graalvm.nativeimage.hosted.Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        /* PNIFunc & PNIRef & GraalThread */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class), PanamaHack.getCriticalOption());
        RuntimeClassInitialization.initializeAtBuildTime(GraalPNIFunc.class);
        RuntimeClassInitialization.initializeAtBuildTime(GraalPNIRef.class);
        RuntimeClassInitialization.initializeAtBuildTime(PanamaHack.class);
        RuntimeClassInitialization.initializeAtBuildTime(GetSetUtf8String.implClass());
        RuntimeClassInitialization.initializeAtBuildTime(VarHandleW.implClass());
        /* ImageInfo */
        RuntimeClassInitialization.initializeAtRunTime(ImageInfoDelegate.class);
        for (var m : ImageInfo.class.getMethods()) {
            RuntimeReflection.register(m);
        }

        /* fubuki_start */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.fubuki.FubukiHandle.LAYOUT.getClass() */, MemoryLayout.class /* io.vproxy.fubuki.FubukiStartOptions.LAYOUT.getClass() */ /* opts */, int.class /* version */, String.class /* errorMsg */));

        /* fubuki_block_on */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, String.class /* errorMsg */));

        /* if_to_fubuki */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, MemorySegment.class /* data */, long.class /* len */), PanamaHack.getCriticalOption());

        /* fubuki_stop */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* graal upcall for io.vproxy.fubuki.FubukiUpcall */
        RuntimeClassInitialization.initializeAtBuildTime(io.vproxy.fubuki.FubukiUpcall.class);
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class), PanamaHack.getCriticalOption());

        /* JavaCritical_io_vproxy_msquic_CxPlatExecutionState___getLayoutByteSize */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(long.class), PanamaHack.getCriticalOption());

        /* JavaCritical_io_vproxy_msquic_CxPlatProcessEventLocals___getLayoutByteSize */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(long.class), PanamaHack.getCriticalOption());

        /* JavaCritical_io_vproxy_msquic_MsQuic_open */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicApiTable.LAYOUT.getClass() */, int.class /* Version */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_MsQuic_buildQuicAddr */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(boolean.class, String.class /* addr */, int.class /* port */, MemoryLayout.class /* io.vproxy.msquic.QuicAddr.LAYOUT.getClass() */ /* result */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod_openExtra */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() */, int.class /* Version */, MemorySegment.class /* returnStatus */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod_INVOKE_LPTHREAD_START_ROUTINE */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* Callback */, MemorySegment.class /* Context */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadInit */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemoryLayout.class /* io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() */ /* api */, MemoryLayout.class /* io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() */ /* CxPlatWorkerThreadLocals */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadBeforePoll */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemoryLayout.class /* io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() */ /* api */, MemoryLayout.class /* io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() */ /* CxPlatProcessEventLocals */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadAfterPoll */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(boolean.class, MemoryLayout.class /* io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() */ /* api */, MemoryLayout.class /* io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() */ /* locals */, int.class /* num */, MemorySegment.class /* events */));

        /* JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadFinalize */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemoryLayout.class /* io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() */ /* api */, MemoryLayout.class /* io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() */ /* CxPlatWorkerThreadLocals */));

        /* graal upcall for io.vproxy.msquic.MsQuicModUpcall */
        RuntimeClassInitialization.initializeAtBuildTime(io.vproxy.msquic.MsQuicModUpcall.class);
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class), PanamaHack.getCriticalOption());

        /* graal upcall for io.vproxy.msquic.MsQuicUpcall */
        RuntimeClassInitialization.initializeAtBuildTime(io.vproxy.msquic.MsQuicUpcall.class);
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class), PanamaHack.getCriticalOption());

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QuicStatusString */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(String.class, int.class /* status */));

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QUIC_STATUS_NOT_SUPPORTED */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class));

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QUIC_STATUS_PENDING */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class));

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QUIC_ADDRESS_FAMILY_UNSPEC */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class));

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QUIC_ADDRESS_FAMILY_INET */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class));

        /* JavaCritical_io_vproxy_msquic_MsQuicValues_QUIC_ADDRESS_FAMILY_INET6 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class));

        /* JavaCritical_io_vproxy_msquic_QuicAddr___getLayoutByteSize */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(long.class), PanamaHack.getCriticalOption());

        /* JavaCritical_io_vproxy_msquic_QuicAddr_getFamily */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicAddr_setFamily */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, int.class /* family */));

        /* JavaCritical_io_vproxy_msquic_QuicAddr_getPort */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicAddr_setPort */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, int.class /* port */));

        /* JavaCritical_io_vproxy_msquic_QuicAddr_toString */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, String.class /* str */));

        /* JavaCritical_io_vproxy_msquic_QuicApiTable_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicApiTable_openRegistration */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicRegistration.LAYOUT.getClass() */, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicRegistrationConfig.LAYOUT.getClass() */ /* Config */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_QuicConfiguration_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicConfiguration_loadCredential */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicCredentialConfig.LAYOUT.getClass() */ /* CredConfig */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_shutdown */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, int.class /* Flags */, long.class /* ErrorCode */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_start */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicConfiguration.LAYOUT.getClass() */ /* Conf */, int.class /* Family */, String.class /* ServerName */, int.class /* ServerPort */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_setConfiguration */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicConfiguration.LAYOUT.getClass() */ /* Conf */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_sendResumptionTicket */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, int.class /* Flags */, int.class /* DataLength */, MemorySegment.class /* ResumptionData */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_openStream */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicStream.LAYOUT.getClass() */, MemorySegment.class /* self */, int.class /* Flags */, MemorySegment.class /* Handler */, MemorySegment.class /* Context */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_sendDatagram */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicBuffer.LAYOUT.getClass() */ /* Buffers */, int.class /* BufferCount */, int.class /* Flags */, MemorySegment.class /* ClientSendContext */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_resumptionTicketValidationComplete */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, boolean.class /* Result */));

        /* JavaCritical_io_vproxy_msquic_QuicConnection_certificateValidationComplete */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, boolean.class /* Result */, int.class /* TlsAlert */));

        /* JavaCritical_io_vproxy_msquic_QuicExtraApiTable_ThreadCountLimitSet */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, int.class /* limit */));

        /* JavaCritical_io_vproxy_msquic_QuicExtraApiTable_EventLoopThreadDispatcherSet */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemorySegment.class /* dispatcher */));

        /* JavaCritical_io_vproxy_msquic_QuicExtraApiTable_ThreadGetCur */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemorySegment.class /* Thread */));

        /* JavaCritical_io_vproxy_msquic_QuicExtraApiTable_ThreadSetIsWorker */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, boolean.class /* isWorker */));

        /* JavaCritical_io_vproxy_msquic_QuicListener_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicListener_start */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemorySegment.class /* AlpnBuffers */, int.class /* AlpnBufferCount */, MemoryLayout.class /* io.vproxy.msquic.QuicAddr.LAYOUT.getClass() */ /* Addr */));

        /* JavaCritical_io_vproxy_msquic_QuicListener_stop */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicObjectBase_setContext */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, MemorySegment.class /* Context */));

        /* JavaCritical_io_vproxy_msquic_QuicObjectBase_getContext */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemorySegment.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicObjectBase_setCallbackHandler */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, MemorySegment.class /* Handler */, MemorySegment.class /* Context */));

        /* JavaCritical_io_vproxy_msquic_QuicObjectBase_setParam */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, int.class /* Param */, int.class /* BufferLength */, MemorySegment.class /* Buffer */));

        /* JavaCritical_io_vproxy_msquic_QuicObjectBase_getParam */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, int.class /* Param */, MemorySegment.class /* BufferLength */, MemorySegment.class /* Buffer */));

        /* JavaCritical_io_vproxy_msquic_QuicRegistration_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicRegistration_shutdown */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, int.class /* Flags */, long.class /* ErrorCode */));

        /* JavaCritical_io_vproxy_msquic_QuicRegistration_openConfiguration */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicConfiguration.LAYOUT.getClass() */, MemorySegment.class /* self */, MemorySegment.class /* AlpnBuffers */, int.class /* AlpnBufferCount */, MemoryLayout.class /* io.vproxy.msquic.QuicSettings.LAYOUT.getClass() */ /* Settings */, MemorySegment.class /* Context */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_QuicRegistration_openListener */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicListener.LAYOUT.getClass() */, MemorySegment.class /* self */, MemorySegment.class /* Handler */, MemorySegment.class /* Context */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_QuicRegistration_openConnection */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(MemoryLayout.class /* io.vproxy.msquic.QuicConnection.LAYOUT.getClass() */, MemorySegment.class /* self */, MemorySegment.class /* Handler */, MemorySegment.class /* Context */, MemorySegment.class /* returnStatus */, MemorySegment.class /* return */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_start */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, int.class /* Flags */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_shutdown */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, int.class /* Flags */, long.class /* ErrorCode */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_send */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, MemoryLayout.class /* io.vproxy.msquic.QuicBuffer.LAYOUT.getClass() */ /* Buffers */, int.class /* BufferCount */, int.class /* Flags */, MemorySegment.class /* ClientSendContext */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_receiveComplete */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(void.class, MemorySegment.class /* self */, long.class /* BufferLength */));

        /* JavaCritical_io_vproxy_msquic_QuicStream_receiveSetEnabled */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildCriticalFunctionDescriptor(int.class, MemorySegment.class /* self */, boolean.class /* IsEnabled */));

        /* Java_io_vproxy_vfd_posix_PosixNative_aeReadable */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeWritable */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_openPipe */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(PNIBuf.class /* fds */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_aeCreateEventLoop */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* setsize */, int.class /* epfd */, boolean.class /* preferPoll */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_aeGetFired */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeGetFiredExtra */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeApiPoll */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */, long.class /* wait */));
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_aeApiPollNow */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_aeGetFiredExtraNum */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeCreateFileEvent */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */, int.class /* fd */, int.class /* mask */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeUpdateFileEvent */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */, int.class /* fd */, int.class /* mask */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeDeleteFileEvent */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */, int.class /* fd */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_aeDeleteEventLoop */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* ae */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_setBlocking */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, boolean.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setSoLinger */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, int.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setReusePort */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, boolean.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setRcvBuf */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, int.class /* buflen */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setTcpNoDelay */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, boolean.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setBroadcast */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, boolean.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setIpTransparent */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, boolean.class /* v */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_close */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createIPv4TcpFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createIPv6TcpFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createIPv4UdpFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createIPv6UdpFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createUnixDomainSocketFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_bindIPv4 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, int.class /* addrHostOrder */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_bindIPv6 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, String.class /* fullAddr */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_bindUnixDomainSocket */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, String.class /* path */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_accept */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_connectIPv4 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, int.class /* addrHostOrder */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_connectIPv6 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, String.class /* fullAddr */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_connectUDS */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, String.class /* sock */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_finishConnect */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_shutdownOutput */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getIPv4Local */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getIPv6Local */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getIPv4Remote */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getIPv6Remote */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getUDSLocal */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_getUDSRemote */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_read */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_readBlocking */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */));
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_write */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv4 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, int.class /* addrHostOrder */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv6 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, String.class /* fullAddr */, int.class /* port */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv4 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv6 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_currentTimeMillis */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_vfd_posix_PosixNative_tapNonBlockingSupported */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_tunNonBlockingSupported */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_createTapFD */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(String.class /* dev */, boolean.class /* isTun */, MemorySegment.class /* return */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_posix_PosixNative_setCoreAffinityForCurrentThread */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* mask */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_IOCP_getQueuedCompletionStatusEx */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() */ /* handle */, MemorySegment.class /* completionPortEntries */, int.class /* count */, int.class /* milliseconds */, boolean.class /* alertable */));
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_IOCP_createIoCompletionPort */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* fileHandle */, MemoryLayout.class /* io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() */ /* existingCompletionPort */, MemorySegment.class /* completionKey */, int.class /* numberOfConcurrentThreads */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_IOCP_postQueuedCompletionStatus */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() */ /* completionPort */, int.class /* numberOfBytesTransferred */, MemorySegment.class /* completionKey */, MemoryLayout.class /* io.vproxy.vfd.windows.Overlapped.LAYOUT.getClass() */ /* overlapped */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_tapNonBlockingSupported */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_createTapHandle */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(String.class /* dev */));
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_closeHandle */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* handle */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_cancelIo */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* handle */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_acceptEx */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* listenSocket */, MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* socketContext */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_updateAcceptContext */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* listenSocket */, MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* accepted */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_tcpConnect */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */, boolean.class /* v4 */, MemoryLayout.class /* io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() */ /* addr */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_wsaRecv */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_wsaRecvFrom */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_readFile */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_wsaSend */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_wsaSendTo */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */, boolean.class /* v4 */, MemoryLayout.class /* io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() */ /* addr */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_wsaSendDisconnect */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() */ /* socket */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_writeFile */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemoryLayout.class /* io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() */ /* ctx */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_vfd_windows_WindowsNative_convertAddress */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(MemorySegment.class /* sockaddr */, boolean.class /* v4 */, MemoryLayout.class /* io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() */ /* addr */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_loadAndAttachBPFProgramToNic */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(String.class /* filepath */, String.class /* programName */, String.class /* nicName */, int.class /* mode */, boolean.class /* forceAttach */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_detachBPFProgramFromNic */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(String.class /* nicName */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_findMapByNameInBPF */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* bpfobj */, String.class /* mapName */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_createUMem */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(int.class /* chunksSize */, int.class /* fillRingSize */, int.class /* compRingSize */, int.class /* frameSize */, int.class /* headroom */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_shareUMem */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_getBufferFromUMem */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_getBufferAddressFromUMem */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_createXSK */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(String.class /* nicName */, int.class /* queueId */, long.class /* umem */, int.class /* rxRingSize */, int.class /* txRingSize */, int.class /* mode */, boolean.class /* zeroCopy */, int.class /* busyPollBudget */, boolean.class /* rxGenChecksum */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_addXSKIntoMap */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* map */, int.class /* key */, long.class /* xsk */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_addMacIntoMap */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* map */, MemorySegment.class /* mac */, long.class /* xsk */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_removeMacFromMap */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* map */, MemorySegment.class /* mac */), PanamaHack.getCriticalOption());
        RuntimeReflection.registerAllConstructors(java.io.IOException.class);
        for (var CONS : java.io.IOException.class.getConstructors()) {
            RuntimeReflection.register(CONS);
        }

        /* Java_io_vproxy_xdp_XDPNative_getFDFromXSK */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_fillUpFillRing */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_fetchPackets0 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */, int.class /* capacity */, MemorySegment.class /* umem */, MemorySegment.class /* chunk */, MemorySegment.class /* ref */, MemorySegment.class /* addr */, MemorySegment.class /* endaddr */, MemorySegment.class /* pktaddr */, MemorySegment.class /* pktlen */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_rxRelease */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */, int.class /* cnt */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_writePacket */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */, long.class /* chunk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_writePackets */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */, int.class /* size */, MemorySegment.class /* chunkPtrs */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_completeTx */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_fetchChunk0 */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umemPtr */, MemorySegment.class /* umem */, MemorySegment.class /* chunk */, MemorySegment.class /* ref */, MemorySegment.class /* addr */, MemorySegment.class /* endaddr */, MemorySegment.class /* pktaddr */, MemorySegment.class /* pktlen */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_setChunk */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* chunk */, int.class /* pktaddr */, int.class /* pktlen */, int.class /* csumFlags */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_releaseChunk */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */, long.class /* chunk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_addChunkRefCnt */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* chunk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_releaseXSK */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* xsk */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_releaseUMem */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* umem */, boolean.class /* releaseBuffer */), PanamaHack.getCriticalOption());

        /* Java_io_vproxy_xdp_XDPNative_releaseBPFObject */
        RuntimeForeignAccess.registerForDowncall(PanamaUtils.buildFunctionDescriptor(long.class /* bpfobj */), PanamaHack.getCriticalOption());
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:38a120bc4306f368794637a078c0c9afb54d1618acdba5a8e1f86295204ec27f
