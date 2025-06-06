/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.annotation.TestApi;
import android.system.Os;
import android.system.OsConstants;
import android.webkit.WebViewZygote;

import dalvik.system.VMRuntime;

/**
 * Tools for managing OS processes.
 */
public class Process {
    private static final String LOG_TAG = "Process";

    /**
     * @hide for internal use only.
     */
    public static final String ZYGOTE_SOCKET = "zygote";

    /**
     * @hide for internal use only.
     */
    public static final String SECONDARY_ZYGOTE_SOCKET = "zygote_secondary";

    /**
     * Defines the root UID.
     * @hide
     */
    public static final int ROOT_UID = 0;

    /**
     * Defines the UID/GID under which system code runs.
     */
    public static final int SYSTEM_UID = 1000;

    /**
     * Defines the UID/GID under which the telephony code runs.
     */
    public static final int PHONE_UID = 1001;

    /**
     * Defines the UID/GID for the user shell.
     * @hide
     */
    public static final int SHELL_UID = 2000;

    /**
     * Defines the UID/GID for the log group.
     * @hide
     */
    public static final int LOG_UID = 1007;

    /**
     * Defines the UID/GID for the WIFI supplicant process.
     * @hide
     */
    public static final int WIFI_UID = 1010;

    /**
     * Defines the UID/GID for the mediaserver process.
     * @hide
     */
    public static final int MEDIA_UID = 1013;

    /**
     * Defines the UID/GID for the DRM process.
     * @hide
     */
    public static final int DRM_UID = 1019;

    /**
     * Defines the UID/GID for the group that controls VPN services.
     * @hide
     */
    public static final int VPN_UID = 1016;

    /**
     * Defines the UID/GID for keystore.
     * @hide
     */
    public static final int KEYSTORE_UID = 1017;

    /**
     * Defines the UID/GID for the NFC service process.
     * @hide
     */
    public static final int NFC_UID = 1027;

    /**
     * Defines the UID/GID for the Bluetooth service process.
     * @hide
     */
    public static final int BLUETOOTH_UID = 1002;

    /**
     * Defines the GID for the group that allows write access to the internal media storage.
     * @hide
     */
    public static final int MEDIA_RW_GID = 1023;

    /**
     * Access to installed package details
     * @hide
     */
    public static final int PACKAGE_INFO_GID = 1032;

    /**
     * Defines the UID/GID for the shared RELRO file updater process.
     * @hide
     */
    public static final int SHARED_RELRO_UID = 1037;

    /**
     * Defines the UID/GID for the audioserver process.
     * @hide
     */
    public static final int AUDIOSERVER_UID = 1041;

    /**
     * Defines the UID/GID for the cameraserver process
     * @hide
     */
    public static final int CAMERASERVER_UID = 1047;

    /**
     * Defines the UID/GID for the WebView zygote process.
     * @hide
     */
    public static final int WEBVIEW_ZYGOTE_UID = 1053;

    /**
     * Defines the UID used for resource tracking for OTA updates.
     * @hide
     */
    public static final int OTA_UPDATE_UID = 1061;

    /**
     * Defines the UID used for incidentd.
     * @hide
     */
    public static final int INCIDENTD_UID = 1067;

    /**
     * Defines the UID/GID for the Secure Element service process.
     * @hide
     */
    public static final int SE_UID = 1068;

    /** {@hide} */
    public static final int NOBODY_UID = 9999;

    /**
     * Defines the start of a range of UIDs (and GIDs), going from this
     * number to {@link #LAST_APPLICATION_UID} that are reserved for assigning
     * to applications.
     */
    public static final int FIRST_APPLICATION_UID = 10000;

    /**
     * Last of application-specific UIDs starting at
     * {@link #FIRST_APPLICATION_UID}.
     */
    public static final int LAST_APPLICATION_UID = 19999;

    /**
     * First uid used for fully isolated sandboxed processes (with no permissions of their own)
     * @hide
     */
    public static final int FIRST_ISOLATED_UID = 99000;

    /**
     * Last uid used for fully isolated sandboxed processes (with no permissions of their own)
     * @hide
     */
    public static final int LAST_ISOLATED_UID = 99999;

    /**
     * Defines the gid shared by all applications running under the same profile.
     * @hide
     */
    public static final int SHARED_USER_GID = 9997;

    /**
     * First gid for applications to share resources. Used when forward-locking
     * is enabled but all UserHandles need to be able to read the resources.
     * @hide
     */
    public static final int FIRST_SHARED_APPLICATION_GID = 50000;

    /**
     * Last gid for applications to share resources. Used when forward-locking
     * is enabled but all UserHandles need to be able to read the resources.
     * @hide
     */
    public static final int LAST_SHARED_APPLICATION_GID = 59999;

    /** {@hide} */
    public static final int FIRST_APPLICATION_CACHE_GID = 20000;
    /** {@hide} */
    public static final int LAST_APPLICATION_CACHE_GID = 29999;

    /**
     * Standard priority of application threads.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_DEFAULT = 0;

    /*
     * ***************************************
     * ** Keep in sync with utils/threads.h **
     * ***************************************
     */
    
    /**
     * Lowest available thread priority.  Only for those who really, really
     * don't want to run if anything else is happening.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_LOWEST = 19;
    
    /**
     * Standard priority background threads.  This gives your thread a slightly
     * lower than normal priority, so that it will have less chance of impacting
     * the responsiveness of the user interface.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    
    /**
     * Standard priority of threads that are currently running a user interface
     * that the user is interacting with.  Applications can not normally
     * change to this priority; the system will automatically adjust your
     * application threads as the user moves through the UI.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    
    /**
     * Standard priority of system display threads, involved in updating
     * the user interface.  Applications can not
     * normally change to this priority.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    
    /**
     * Standard priority of the most important display threads, for compositing
     * the screen and retrieving input events.  Applications can not normally
     * change to this priority.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;

    /**
     * Standard priority of video threads.  Applications can not normally
     * change to this priority.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_VIDEO = -10;

    /**
     * Standard priority of audio threads.  Applications can not normally
     * change to this priority.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_AUDIO = -16;

    /**
     * Standard priority of the most important audio threads.
     * Applications can not normally change to this priority.
     * Use with {@link #setThreadPriority(int)} and
     * {@link #setThreadPriority(int, int)}, <b>not</b> with the normal
     * {@link java.lang.Thread} class.
     */
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;

    /**
     * Minimum increment to make a priority more favorable.
     */
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;

    /**
     * Minimum increment to make a priority less favorable.
     */
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = +1;

    /**
     * Default scheduling policy
     * @hide
     */
    public static final int SCHED_OTHER = 0;

    /**
     * First-In First-Out scheduling policy
     * @hide
     */
    public static final int SCHED_FIFO = 1;

    /**
     * Round-Robin scheduling policy
     * @hide
     */
    public static final int SCHED_RR = 2;

    /**
     * Batch scheduling policy
     * @hide
     */
    public static final int SCHED_BATCH = 3;

    /**
     * Idle scheduling policy
     * @hide
     */
    public static final int SCHED_IDLE = 5;

    /**
     * Reset scheduler choice on fork.
     * @hide
     */
    public static final int SCHED_RESET_ON_FORK = 0x40000000;

    // Keep in sync with SP_* constants of enum type SchedPolicy
    // declared in system/core/include/cutils/sched_policy.h,
    // except THREAD_GROUP_DEFAULT does not correspond to any SP_* value.

    /**
     * Default thread group -
     * has meaning with setProcessGroup() only, cannot be used with setThreadGroup().
     * When used with setProcessGroup(), the group of each thread in the process
     * is conditionally changed based on that thread's current priority, as follows:
     * threads with priority numerically less than THREAD_PRIORITY_BACKGROUND
     * are moved to foreground thread group.  All other threads are left unchanged.
     * @hide
     */
    public static final int THREAD_GROUP_DEFAULT = -1;

    /**
     * Background thread group - All threads in
     * this group are scheduled with a reduced share of the CPU.
     * Value is same as constant SP_BACKGROUND of enum SchedPolicy.
     * FIXME rename to THREAD_GROUP_BACKGROUND.
     * @hide
     */
    public static final int THREAD_GROUP_BG_NONINTERACTIVE = 0;

    /**
     * Foreground thread group - All threads in
     * this group are scheduled with a normal share of the CPU.
     * Value is same as constant SP_FOREGROUND of enum SchedPolicy.
     * Not used at this level.
     * @hide
     **/
    private static final int THREAD_GROUP_FOREGROUND = 1;

    /**
     * System thread group.
     * @hide
     **/
    public static final int THREAD_GROUP_SYSTEM = 2;

    /**
     * Application audio thread group.
     * @hide
     **/
    public static final int THREAD_GROUP_AUDIO_APP = 3;

    /**
     * System audio thread group.
     * @hide
     **/
    public static final int THREAD_GROUP_AUDIO_SYS = 4;

    /**
     * Thread group for top foreground app.
     * @hide
     **/
    public static final int THREAD_GROUP_TOP_APP = 5;

    /**
     * Thread group for RT app.
     * @hide
     **/
    public static final int THREAD_GROUP_RT_APP = 6;

    /**
     * Thread group for bound foreground services that should
     * have additional CPU restrictions during screen off
     * @hide
     **/
    public static final int THREAD_GROUP_RESTRICTED = 7;

    public static final int SIGNAL_QUIT = 3;
    public static final int SIGNAL_KILL = 9;
    public static final int SIGNAL_USR1 = 10;

    private static long sStartElapsedRealtime;
    private static long sStartUptimeMillis;

    /**
     * State associated with the zygote process.
     * @hide
     */
    public static final ZygoteProcess zygoteProcess =
            new ZygoteProcess(ZYGOTE_SOCKET, SECONDARY_ZYGOTE_SOCKET);

    /**
     * Start a new process.
     * 
     * <p>If processes are enabled, a new process is created and the
     * static main() function of a <var>processClass</var> is executed there.
     * The process will continue running after this function returns.
     * 
     * <p>If processes are not enabled, a new thread in the caller's
     * process is created and main() of <var>processClass</var> called there.
     * 
     * <p>The niceName parameter, if not an empty string, is a custom name to
     * give to the process instead of using processClass.  This allows you to
     * make easily identifyable processes even if you are using the same base
     * <var>processClass</var> to start them.
     * 
     * When invokeWith is not null, the process will be started as a fresh app
     * and not a zygote fork. Note that this is only allowed for uid 0 or when
     * runtimeFlags contains DEBUG_ENABLE_DEBUGGER.
     *
     * @param processClass The class to use as the process's main entry
     *                     point.
     * @param niceName A more readable name to use for the process.
     * @param uid The user-id under which the process will run.
     * @param gid The group-id under which the process will run.
     * @param gids Additional group-ids associated with the process.
     * @param runtimeFlags Additional flags for the runtime.
     * @param targetSdkVersion The target SDK version for the app.
     * @param seInfo null-ok SELinux information for the new process.
     * @param abi non-null the ABI this app should be started with.
     * @param instructionSet null-ok the instruction set to use.
     * @param appDataDir null-ok the data directory of the app.
     * @param invokeWith null-ok the command to invoke with.
     * @param zygoteArgs Additional arguments to supply to the zygote process.
     * 
     * @return An object that describes the result of the attempt to start the process.
     * @throws RuntimeException on fatal start failure
     * 
     * {@hide}
     */
    public static final ProcessStartResult start(final String processClass,
                                  final String niceName,
                                  int uid, int gid, int[] gids,
                                  int runtimeFlags, int mountExternal,
                                  int targetSdkVersion,
                                  String seInfo,
                                  String abi,
                                  String instructionSet,
                                  String appDataDir,
                                  String invokeWith,
                                  String[] zygoteArgs) {
        return zygoteProcess.start(processClass, niceName, uid, gid, gids,
                    runtimeFlags, mountExternal, targetSdkVersion, seInfo,
                    abi, instructionSet, appDataDir, invokeWith, zygoteArgs);
    }

    /** @hide */
    public static final ProcessStartResult startWebView(final String processClass,
                                  final String niceName,
                                  int uid, int gid, int[] gids,
                                  int runtimeFlags, int mountExternal,
                                  int targetSdkVersion,
                                  String seInfo,
                                  String abi,
                                  String instructionSet,
                                  String appDataDir,
                                  String invokeWith,
                                  String[] zygoteArgs) {
        return WebViewZygote.getProcess().start(processClass, niceName, uid, gid, gids,
                    runtimeFlags, mountExternal, targetSdkVersion, seInfo,
                    abi, instructionSet, appDataDir, invokeWith, zygoteArgs);
    }

    /**
     * Returns elapsed milliseconds of the time this process has run.
     * @return  Returns the number of milliseconds this process has return.
     */
    public static final native long getElapsedCpuTime();

    /**
     * Return the {@link SystemClock#elapsedRealtime()} at which this process was started.
     */
    public static final long getStartElapsedRealtime() {
        return sStartElapsedRealtime;
    }

    /**
     * Return the {@link SystemClock#uptimeMillis()} at which this process was started.
     */
    public static final long getStartUptimeMillis() {
        return sStartUptimeMillis;
    }

    /** @hide */
    public static final void setStartTimes(long elapsedRealtime, long uptimeMillis) {
        sStartElapsedRealtime = elapsedRealtime;
        sStartUptimeMillis = uptimeMillis;
    }

    /**
     * Returns true if the current process is a 64-bit runtime.
     */
    public static final boolean is64Bit() {
        return VMRuntime.getRuntime().is64Bit();
    }

    /**
     * Returns the identifier of this process, which can be used with
     * {@link #killProcess} and {@link #sendSignal}.
     */
    public static final int myPid() {
        return Os.getpid();
    }

    /**
     * Returns the identifier of this process' parent.
     * @hide
     */
    public static final int myPpid() {
        return Os.getppid();
    }

    /**
     * Returns the identifier of the calling thread, which be used with
     * {@link #setThreadPriority(int, int)}.
     */
    public static final int myTid() {
        return Os.gettid();
    }

    /**
     * Returns the identifier of this process's uid.  This is the kernel uid
     * that the process is running under, which is the identity of its
     * app-specific sandbox.  It is different from {@link #myUserHandle} in that
     * a uid identifies a specific app sandbox in a specific user.
     */
    public static final int myUid() {
        return Os.getuid();
    }

    /**
     * Returns this process's user handle.  This is the
     * user the process is running under.  It is distinct from
     * {@link #myUid()} in that a particular user will have multiple
     * distinct apps running under it each with their own uid.
     */
    public static UserHandle myUserHandle() {
        return UserHandle.of(UserHandle.getUserId(myUid()));
    }

    /**
     * Returns whether the given uid belongs to a system core component or not.
     * @hide
     */
    public static boolean isCoreUid(int uid) {
        return UserHandle.isCore(uid);
    }

    /**
     * Returns whether the given uid belongs to an application.
     * @param uid A kernel uid.
     * @return Whether the uid corresponds to an application sandbox running in
     *     a specific user.
     */
    public static boolean isApplicationUid(int uid) {
        return UserHandle.isApp(uid);
    }

    /**
     * Returns whether the current process is in an isolated sandbox.
     */
    public static final boolean isIsolated() {
        return isIsolated(myUid());
    }

    /** {@hide} */
    public static final boolean isIsolated(int uid) {
        uid = UserHandle.getAppId(uid);
        return uid >= FIRST_ISOLATED_UID && uid <= LAST_ISOLATED_UID;
    }

    /**
     * Returns the UID assigned to a particular user name, or -1 if there is
     * none.  If the given string consists of only numbers, it is converted
     * directly to a uid.
     */
    public static final native int getUidForName(String name);
    
    /**
     * Returns the GID assigned to a particular user name, or -1 if there is
     * none.  If the given string consists of only numbers, it is converted
     * directly to a gid.
     */
    public static final native int getGidForName(String name);

    /**
     * Returns a uid for a currently running process.
     * @param pid the process id
     * @return the uid of the process, or -1 if the process is not running.
     * @hide pending API council review
     */
    public static final int getUidForPid(int pid) {
        String[] procStatusLabels = { "Uid:" };
        long[] procStatusValues = new long[1];
        procStatusValues[0] = -1;
        Process.readProcLines("/proc/" + pid + "/status", procStatusLabels, procStatusValues);
        return (int) procStatusValues[0];
    }

    /**
     * Returns the parent process id for a currently running process.
     * @param pid the process id
     * @return the parent process id of the process, or -1 if the process is not running.
     * @hide
     */
    public static final int getParentPid(int pid) {
        String[] procStatusLabels = { "PPid:" };
        long[] procStatusValues = new long[1];
        procStatusValues[0] = -1;
        Process.readProcLines("/proc/" + pid + "/status", procStatusLabels, procStatusValues);
        return (int) procStatusValues[0];
    }

    /**
     * Returns the thread group leader id for a currently running thread.
     * @param tid the thread id
     * @return the thread group leader id of the thread, or -1 if the thread is not running.
     *         This is same as what getpid(2) would return if called by tid.
     * @hide
     */
    public static final int getThreadGroupLeader(int tid) {
        String[] procStatusLabels = { "Tgid:" };
        long[] procStatusValues = new long[1];
        procStatusValues[0] = -1;
        Process.readProcLines("/proc/" + tid + "/status", procStatusLabels, procStatusValues);
        return (int) procStatusValues[0];
    }

    /**
     * Set the priority of a thread, based on Linux priorities.
     * 
     * @param tid The identifier of the thread/process to change.
     * @param priority A Linux priority level, from -20 for highest scheduling
     * priority to 19 for lowest scheduling priority.
     * 
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * priority.
     */
    public static final native void setThreadPriority(int tid, int priority)
            throws IllegalArgumentException, SecurityException;

    /**
     * Call with 'false' to cause future calls to {@link #setThreadPriority(int)} to
     * throw an exception if passed a background-level thread priority.  This is only
     * effective if the JNI layer is built with GUARD_THREAD_PRIORITY defined to 1.
     *
     * @hide
     */
    public static final native void setCanSelfBackground(boolean backgroundOk);

    /**
     * Sets the scheduling group for a thread.
     * @hide
     * @param tid The identifier of the thread to change.
     * @param group The target group for this thread from THREAD_GROUP_*.
     *
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * priority.
     * If the thread is a thread group leader, that is it's gettid() == getpid(),
     * then the other threads in the same thread group are _not_ affected.
     *
     * Does not set cpuset for some historical reason, just calls
     * libcutils::set_sched_policy().
     */
    public static final native void setThreadGroup(int tid, int group)
            throws IllegalArgumentException, SecurityException;

    /**
     * Sets the scheduling group and the corresponding cpuset group
     * @hide
     * @param tid The identifier of the thread to change.
     * @param group The target group for this thread from THREAD_GROUP_*.
     *
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * priority.
     */
    public static final native void setThreadGroupAndCpuset(int tid, int group)
            throws IllegalArgumentException, SecurityException;

    /**
     * Sets the scheduling group for a process and all child threads
     * @hide
     * @param pid The identifier of the process to change.
     * @param group The target group for this process from THREAD_GROUP_*.
     * 
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * priority.
     *
     * group == THREAD_GROUP_DEFAULT means to move all non-background priority
     * threads to the foreground scheduling group, but to leave background
     * priority threads alone.  group == THREAD_GROUP_BG_NONINTERACTIVE moves all
     * threads, regardless of priority, to the background scheduling group.
     * group == THREAD_GROUP_FOREGROUND is not allowed.
     *
     * Always sets cpusets.
     */
    public static final native void setProcessGroup(int pid, int group)
            throws IllegalArgumentException, SecurityException;

    /**
     * Return the scheduling group of requested process.
     *
     * @hide
     */
    public static final native int getProcessGroup(int pid)
            throws IllegalArgumentException, SecurityException;

    /**
     * On some devices, the foreground process may have one or more CPU
     * cores exclusively reserved for it. This method can be used to
     * retrieve which cores that are (if any), so the calling process
     * can then use sched_setaffinity() to lock a thread to these cores.
     * Note that the calling process must currently be running in the
     * foreground for this method to return any cores.
     *
     * The CPU core(s) exclusively reserved for the foreground process will
     * stay reserved for as long as the process stays in the foreground.
     *
     * As soon as a process leaves the foreground, those CPU cores will
     * no longer be reserved for it, and will most likely be reserved for
     * the new foreground process. It's not necessary to change the affinity
     * of your process when it leaves the foreground (if you had previously
     * set it to use a reserved core); the OS will automatically take care
     * of resetting the affinity at that point.
     *
     * @return an array of integers, indicating the CPU cores exclusively
     * reserved for this process. The array will have length zero if no
     * CPU cores are exclusively reserved for this process at this point
     * in time.
     */
    public static final native int[] getExclusiveCores();

    /**
     * Set the priority of the calling thread, based on Linux priorities.  See
     * {@link #setThreadPriority(int, int)} for more information.
     * 
     * @param priority A Linux priority level, from -20 for highest scheduling
     * priority to 19 for lowest scheduling priority.
     * 
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * priority.
     * 
     * @see #setThreadPriority(int, int)
     */
    public static final native void setThreadPriority(int priority)
            throws IllegalArgumentException, SecurityException;
    
    /**
     * Return the current priority of a thread, based on Linux priorities.
     * 
     * @param tid The identifier of the thread/process. If tid equals zero, the priority of the
     * calling process/thread will be returned.
     * 
     * @return Returns the current priority, as a Linux priority level,
     * from -20 for highest scheduling priority to 19 for lowest scheduling
     * priority.
     * 
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist.
     */
    public static final native int getThreadPriority(int tid)
            throws IllegalArgumentException;
    
    /**
     * Return the current scheduling policy of a thread, based on Linux.
     *
     * @param tid The identifier of the thread/process to get the scheduling policy.
     *
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist, or if <var>priority</var> is out of range for the policy.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * scheduling policy or priority.
     *
     * {@hide}
     */
    
    @TestApi
    public static final native int getThreadScheduler(int tid)
            throws IllegalArgumentException;

    /**
     * Set the scheduling policy and priority of a thread, based on Linux.
     *
     * @param tid The identifier of the thread/process to change.
     * @param policy A Linux scheduling policy such as SCHED_OTHER etc.
     * @param priority A Linux priority level in a range appropriate for the given policy.
     *
     * @throws IllegalArgumentException Throws IllegalArgumentException if
     * <var>tid</var> does not exist, or if <var>priority</var> is out of range for the policy.
     * @throws SecurityException Throws SecurityException if your process does
     * not have permission to modify the given thread, or to use the given
     * scheduling policy or priority.
     *
     * {@hide}
     */

    public static final native void setThreadScheduler(int tid, int policy, int priority)
            throws IllegalArgumentException;

    /**
     * Determine whether the current environment supports multiple processes.
     * 
     * @return Returns true if the system can run in multiple processes, else
     * false if everything is running in a single process.
     *
     * @deprecated This method always returns true.  Do not use.
     */
    @Deprecated
    public static final boolean supportsProcesses() {
        return true;
    }

    /**
     * Adjust the swappiness level for a process.
     *
     * @param pid The process identifier to set.
     * @param is_increased Whether swappiness should be increased or default.
     *
     * @return Returns true if the underlying system supports this
     *         feature, else false.
     *
     * {@hide}
     */
    public static final native boolean setSwappiness(int pid, boolean is_increased);

    /**
     * Change this process's argv[0] parameter.  This can be useful to show
     * more descriptive information in things like the 'ps' command.
     * 
     * @param text The new name of this process.
     * 
     * {@hide}
     */
    public static final native void setArgV0(String text);

    /**
     * Kill the process with the given PID.
     * Note that, though this API allows us to request to
     * kill any process based on its PID, the kernel will
     * still impose standard restrictions on which PIDs you
     * are actually able to kill.  Typically this means only
     * the process running the caller's packages/application
     * and any additional processes created by that app; packages
     * sharing a common UID will also be able to kill each
     * other's processes.
     */
    public static final void killProcess(int pid) {
        sendSignal(pid, SIGNAL_KILL);
    }

    /** @hide */
    public static final native int setUid(int uid);

    /** @hide */
    public static final native int setGid(int uid);

    /**
     * Send a signal to the given process.
     * 
     * @param pid The pid of the target process.
     * @param signal The signal to send.
     */
    public static final native void sendSignal(int pid, int signal);
    
    /**
     * @hide
     * Private impl for avoiding a log message...  DO NOT USE without doing
     * your own log, or the Android Illuminati will find you some night and
     * beat you up.
     */
    public static final void killProcessQuiet(int pid) {
        sendSignalQuiet(pid, SIGNAL_KILL);
    }

    /**
     * @hide
     * Private impl for avoiding a log message...  DO NOT USE without doing
     * your own log, or the Android Illuminati will find you some night and
     * beat you up.
     */
    public static final native void sendSignalQuiet(int pid, int signal);
    
    /** @hide */
    public static final native long getFreeMemory();
    
    /** @hide */
    public static final native long getTotalMemory();

    /** @hide */
    public static final native long getTotalMemoryBySysinfo();
    
    /** @hide */
    public static final native void readProcLines(String path,
            String[] reqFields, long[] outSizes);
    
    /** @hide */
    public static final native int[] getPids(String path, int[] lastArray);
    
    /** @hide */
    public static final int PROC_TERM_MASK = 0xff;
    /** @hide */
    public static final int PROC_ZERO_TERM = 0;
    /** @hide */
    public static final int PROC_SPACE_TERM = (int)' ';
    /** @hide */
    public static final int PROC_TAB_TERM = (int)'\t';
    /** @hide */
    public static final int PROC_COMBINE = 0x100;
    /** @hide */
    public static final int PROC_PARENS = 0x200;
    /** @hide */
    public static final int PROC_QUOTES = 0x400;
    /** @hide */
    public static final int PROC_CHAR = 0x800;
    /** @hide */
    public static final int PROC_OUT_STRING = 0x1000;
    /** @hide */
    public static final int PROC_OUT_LONG = 0x2000;
    /** @hide */
    public static final int PROC_OUT_FLOAT = 0x4000;
    
    /** @hide */
    public static final native boolean readProcFile(String file, int[] format,
            String[] outStrings, long[] outLongs, float[] outFloats);
    
    /** @hide */
    public static final native boolean parseProcLine(byte[] buffer, int startIndex, 
            int endIndex, int[] format, String[] outStrings, long[] outLongs, float[] outFloats);

    /** @hide */
    public static final native int[] getPidsForCommands(String[] cmds);

    /**
     * Gets the total Pss value for a given process, in bytes.
     * 
     * @param pid the process to the Pss for
     * @return the total Pss value for the given process in bytes,
     *  or -1 if the value cannot be determined 
     * @hide
     */
    public static final native long getPss(int pid);

    /**
     * Specifies the outcome of having started a process.
     * @hide
     */
    public static final class ProcessStartResult {
        /**
         * The PID of the newly started process.
         * Always >= 0.  (If the start failed, an exception will have been thrown instead.)
         */
        public int pid;

        /**
         * True if the process was started with a wrapper attached.
         */
        public boolean usingWrapper;
    }

    /**
     * Kill all processes in a process group started for the given
     * pid.
     * @hide
     */
    public static final native int killProcessGroup(int uid, int pid);

    /**
     * Remove all process groups.  Expected to be called when ActivityManager
     * is restarted.
     * @hide
     */
    public static final native void removeAllProcessGroups();

    /**
     * Check to see if a thread belongs to a given process. This may require
     * more permissions than apps generally have.
     * @return true if this thread belongs to a process
     * @hide
     */
    public static final boolean isThreadInProcess(int tid, int pid) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            if (Os.access("/proc/" + tid + "/task/" + pid, OsConstants.F_OK)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

    }
}
