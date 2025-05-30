/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.pm;

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;

import android.accounts.IAccountManager;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.content.pm.PackageParser.ApkLite;
import android.content.pm.PackageParser.PackageLite;
import android.content.pm.PackageParser.PackageParserException;
import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.pm.VersionedPackage;
import android.content.pm.dex.ArtManager;
import android.content.pm.dex.DexMetadataHelper;
import android.content.pm.dex.ISnapshotRuntimeProfileCallback;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IUserManager;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArraySet;
import android.util.PrintWriterPrinter;
import com.android.internal.content.PackageHelper;
import com.android.internal.util.ArrayUtils;
import com.android.server.LocalServices;
import com.android.server.SystemConfig;
import dalvik.system.DexFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import libcore.io.IoUtils;
import libcore.io.Streams;

class PackageManagerShellCommand extends ShellCommand {
    /** Path for streaming APK content */
    private static final String STDIN_PATH = "-";
    /** Path where ART profiles snapshots are dumped for the shell user */
    private final static String ART_PROFILE_SNAPSHOT_DEBUG_LOCATION = "/data/misc/profman/";

    final IPackageManager mInterface;
    final private WeakHashMap<String, Resources> mResourceCache =
            new WeakHashMap<String, Resources>();
    int mTargetUser;
    boolean mBrief;
    boolean mComponents;
    final HwPackageManagerShellCommand mHwPackageManagerShellCommand;

    PackageManagerShellCommand(PackageManagerService service) {
        mInterface = service;
        mHwPackageManagerShellCommand = new HwPackageManagerShellCommand(this, service);
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }

        final PrintWriter pw = getOutPrintWriter();
        try {
            switch(cmd) {
                case "path":
                    return runPath();
                case "dump":
                    return runDump();
                case "list":
                    return runList();
                case "resolve-activity":
                    return runResolveActivity();
                case "query-activities":
                    return runQueryIntentActivities();
                case "query-services":
                    return runQueryIntentServices();
                case "query-receivers":
                    return runQueryIntentReceivers();
                case "install":
                    return runInstall();
                case "install-abandon":
                case "install-destroy":
                    return runInstallAbandon();
                case "install-commit":
                    return runInstallCommit();
                case "install-create":
                    return runInstallCreate();
                case "install-remove":
                    return runInstallRemove();
                case "install-write":
                    return runInstallWrite();
                case "install-existing":
                    return runInstallExisting();
                case "set-install-location":
                    return runSetInstallLocation();
                case "get-install-location":
                    return runGetInstallLocation();
                case "move-package":
                    return runMovePackage();
                case "move-primary-storage":
                    return runMovePrimaryStorage();
                case "compile":
                    return runCompile();
                case "reconcile-secondary-dex-files":
                    return runreconcileSecondaryDexFiles();
                case "force-dex-opt":
                    return runForceDexOpt();
                case "bg-dexopt-job":
                    return runDexoptJob();
                case "dump-profiles":
                    return runDumpProfiles();
                case "snapshot-profile":
                    return runSnapshotProfile();
                case "uninstall":
                    return runUninstall();
                case "clear":
                    return runClear();
                case "enable":
                    return runSetEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
                case "disable":
                    return runSetEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
                case "disable-user":
                    return runSetEnabledSetting(
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
                case "disable-until-used":
                    return runSetEnabledSetting(
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
                case "default-state":
                    return runSetEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
                case "hide":
                    return runSetHiddenSetting(true);
                case "unhide":
                    return runSetHiddenSetting(false);
                case "suspend":
                    return runSuspend(true);
                case "unsuspend":
                    return runSuspend(false);
                case "grant":
                    return runGrantRevokePermission(true);
                case "revoke":
                    return runGrantRevokePermission(false);
                case "reset-permissions":
                    return runResetPermissions();
                case "set-permission-enforced":
                    return runSetPermissionEnforced();
                case "get-privapp-permissions":
                    return runGetPrivappPermissions();
                case "get-privapp-deny-permissions":
                    return runGetPrivappDenyPermissions();
                case "get-oem-permissions":
                    return runGetOemPermissions();
                case "set-app-link":
                    return runSetAppLink();
                case "get-app-link":
                    return runGetAppLink();
                case "trim-caches":
                    return runTrimCaches();
                case "create-user":
                    return runCreateUser();
                case "remove-user":
                    return runRemoveUser();
                case "set-user-restriction":
                    return runSetUserRestriction();
                case "get-max-users":
                    return runGetMaxUsers();
                case "get-max-running-users":
                    return runGetMaxRunningUsers();
                case "set-home-activity":
                    return runSetHomeActivity();
                case "set-installer":
                    return runSetInstaller();
                case "get-instantapp-resolver":
                    return runGetInstantAppResolver();
                case "has-feature":
                    return runHasFeature();
                case "set-harmful-app-warning":
                    return runSetHarmfulAppWarning();
                case "get-harmful-app-warning":
                    return runGetHarmfulAppWarning();
                case "uninstall-system-updates":
                    return uninstallSystemUpdates();
                default: {
                    int ret = mHwPackageManagerShellCommand.onCommand(cmd);
                    if (ret >= 0) {
                        return ret;
                    }
                    String nextArg = getNextArg();
                    if (nextArg == null) {
                        if (cmd.equalsIgnoreCase("-l")) {
                            return runListPackages(false);
                        } else if (cmd.equalsIgnoreCase("-lf")) {
                            return runListPackages(true);
                        }
                    } else if (getNextArg() == null) {
                        if (cmd.equalsIgnoreCase("-p")) {
                            return displayPackageFilePath(nextArg, UserHandle.USER_SYSTEM);
                        }
                    }
                    return handleDefaultCommands(cmd);
                }
            }
        } catch (RemoteException e) {
            pw.println("Remote exception: " + e);
        }
        return -1;
    }

    private int uninstallSystemUpdates() {
        final PrintWriter pw = getOutPrintWriter();
        List<String> failedUninstalls = new LinkedList<>();
        try {
            final ParceledListSlice<ApplicationInfo> packages =
                    mInterface.getInstalledApplications(
                            PackageManager.MATCH_SYSTEM_ONLY, UserHandle.USER_SYSTEM);
            final IPackageInstaller installer = mInterface.getPackageInstaller();
            List<ApplicationInfo> list = packages.getList();
            for (ApplicationInfo info : list) {
                if (info.isUpdatedSystemApp()) {
                    pw.println("Uninstalling updates to " + info.packageName + "...");
                    final LocalIntentReceiver receiver = new LocalIntentReceiver();
                    installer.uninstall(new VersionedPackage(info.packageName,
                                    info.versionCode), null /*callerPackageName*/, 0 /* flags */,
                            receiver.getIntentSender(), 0);

                    final Intent result = receiver.getResult();
                    final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE);
                    if (status != PackageInstaller.STATUS_SUCCESS) {
                        failedUninstalls.add(info.packageName);
                    }
                }
            }
        } catch (RemoteException e) {
            pw.println("Failure ["
                    + e.getClass().getName() + " - "
                    + e.getMessage() + "]");
            return 0;
        }
        if (!failedUninstalls.isEmpty()) {
            pw.println("Failure [Couldn't uninstall packages: "
                    + TextUtils.join(", ", failedUninstalls)
                    + "]");
            return 0;
        }
        pw.println("Success");
        return 1;
    }

    private void setParamsSize(InstallParams params, String inPath) {
        if (params.sessionParams.sizeBytes == -1 && !STDIN_PATH.equals(inPath)) {
            final ParcelFileDescriptor fd = openFileForSystem(inPath, "r");
            if (fd == null) {
                getErrPrintWriter().println("Error: Can't open file: " + inPath);
                throw new IllegalArgumentException("Error: Can't open file: " + inPath);
            }
            try {
                ApkLite baseApk = PackageParser.parseApkLite(fd.getFileDescriptor(), inPath, 0);
                PackageLite pkgLite = new PackageLite(null, baseApk, null, null, null, null,
                        null, null);
                params.sessionParams.setSize(PackageHelper.calculateInstalledSize(
                        pkgLite, params.sessionParams.abiOverride, fd.getFileDescriptor()));
            } catch (PackageParserException | IOException e) {
                getErrPrintWriter().println("Error: Failed to parse APK file: " + inPath);
                throw new IllegalArgumentException(
                        "Error: Failed to parse APK file: " + inPath, e);
            } finally {
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
        }
    }
    /**
     * Displays the package file for a package.
     * @param pckg
     */
    private int displayPackageFilePath(String pckg, int userId) throws RemoteException {
        PackageInfo info = mInterface.getPackageInfo(pckg, 0, userId);
        if (info != null && info.applicationInfo != null) {
            final PrintWriter pw = getOutPrintWriter();
            pw.print("package:");
            pw.println(info.applicationInfo.sourceDir);
            if (!ArrayUtils.isEmpty(info.applicationInfo.splitSourceDirs)) {
                for (String splitSourceDir : info.applicationInfo.splitSourceDirs) {
                    pw.print("package:");
                    pw.println(splitSourceDir);
                }
            }
            return 0;
        }
        return 1;
    }

    private int runPath() throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;
        String option = getNextOption();
        if (option != null && option.equals("--user")) {
            userId = UserHandle.parseUserArg(getNextArgRequired());
        }

        String pkg = getNextArgRequired();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified");
            return 1;
        }
        return displayPackageFilePath(pkg, userId);
    }

    private int runList() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final String type = getNextArg();
        if (type == null) {
            pw.println("Error: didn't specify type of data to list");
            return -1;
        }
        switch(type) {
            case "features":
                return runListFeatures();
            case "instrumentation":
                return runListInstrumentation();
            case "libraries":
                return runListLibraries();
            case "package":
            case "packages":
                return runListPackages(false /*showSourceDir*/);
            case "permission-groups":
                return runListPermissionGroups();
            case "permissions":
                return runListPermissions();
            case "users":
                ServiceManager.getService("user").shellCommand(
                        getInFileDescriptor(), getOutFileDescriptor(), getErrFileDescriptor(),
                        new String[] { "list" }, getShellCallback(), adoptResultReceiver());
                return 0;
        }
        pw.println("Error: unknown list type '" + type + "'");
        return -1;
    }

    private int runListFeatures() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final List<FeatureInfo> list = mInterface.getSystemAvailableFeatures().getList();

        // sort by name
        Collections.sort(list, new Comparator<FeatureInfo>() {
            public int compare(FeatureInfo o1, FeatureInfo o2) {
                if (o1.name == o2.name) return 0;
                if (o1.name == null) return -1;
                if (o2.name == null) return 1;
                return o1.name.compareTo(o2.name);
            }
        });

        final int count = (list != null) ? list.size() : 0;
        for (int p = 0; p < count; p++) {
            FeatureInfo fi = list.get(p);
            pw.print("feature:");
            if (fi.name != null) {
                pw.print(fi.name);
                if (fi.version > 0) {
                    pw.print("=");
                    pw.print(fi.version);
                }
                pw.println();
            } else {
                pw.println("reqGlEsVersion=0x"
                        + Integer.toHexString(fi.reqGlEsVersion));
            }
        }
        return 0;
    }

    private int runListInstrumentation() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        boolean showSourceDir = false;
        String targetPackage = null;

        try {
            String opt;
            while ((opt = getNextArg()) != null) {
                switch (opt) {
                    case "-f":
                        showSourceDir = true;
                        break;
                    default:
                        if (opt.charAt(0) != '-') {
                            targetPackage = opt;
                        } else {
                            pw.println("Error: Unknown option: " + opt);
                            return -1;
                        }
                        break;
                }
            }
        } catch (RuntimeException ex) {
            pw.println("Error: " + ex.toString());
            return -1;
        }

        final List<InstrumentationInfo> list =
                mInterface.queryInstrumentation(targetPackage, 0 /*flags*/).getList();

        // sort by target package
        Collections.sort(list, new Comparator<InstrumentationInfo>() {
            public int compare(InstrumentationInfo o1, InstrumentationInfo o2) {
                return o1.targetPackage.compareTo(o2.targetPackage);
            }
        });

        final int count = (list != null) ? list.size() : 0;
        for (int p = 0; p < count; p++) {
            final InstrumentationInfo ii = list.get(p);
            pw.print("instrumentation:");
            if (showSourceDir) {
                pw.print(ii.sourceDir);
                pw.print("=");
            }
            final ComponentName cn = new ComponentName(ii.packageName, ii.name);
            pw.print(cn.flattenToShortString());
            pw.print(" (target=");
            pw.print(ii.targetPackage);
            pw.println(")");
        }
        return 0;
    }

    private int runListLibraries() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final List<String> list = new ArrayList<String>();
        final String[] rawList = mInterface.getSystemSharedLibraryNames();
        for (int i = 0; i < rawList.length; i++) {
            list.add(rawList[i]);
        }

        // sort by name
        Collections.sort(list, new Comparator<String>() {
            public int compare(String o1, String o2) {
                if (o1 == o2) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                return o1.compareTo(o2);
            }
        });

        final int count = (list != null) ? list.size() : 0;
        for (int p = 0; p < count; p++) {
            String lib = list.get(p);
            pw.print("library:");
            pw.println(lib);
        }
        return 0;
    }

    private int runListPackages(boolean showSourceDir) throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        int getFlags = 0;
        boolean listDisabled = false, listEnabled = false;
        boolean listSystem = false, listThirdParty = false;
        boolean listInstaller = false;
        boolean showUid = false;
        boolean showVersionCode = false;
        int uid = -1;
        int userId = UserHandle.USER_SYSTEM;
        try {
            String opt;
            while ((opt = getNextOption()) != null) {
                switch (opt) {
                    case "-d":
                        listDisabled = true;
                        break;
                    case "-e":
                        listEnabled = true;
                        break;
                    case "-f":
                        showSourceDir = true;
                        break;
                    case "-i":
                        listInstaller = true;
                        break;
                    case "-l":
                        // old compat
                        break;
                    case "-s":
                        listSystem = true;
                        break;
                    case "-U":
                        showUid = true;
                        break;
                    case "-u":
                        getFlags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
                        break;
                    case "-3":
                        listThirdParty = true;
                        break;
                    case "--show-versioncode":
                        showVersionCode = true;
                        break;
                    case "--user":
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                        break;
                    case "--uid":
                        showUid = true;
                        uid = Integer.parseInt(getNextArgRequired());
                        break;
                    default:
                        pw.println("Error: Unknown option: " + opt);
                        return -1;
                }
            }
        } catch (RuntimeException ex) {
            pw.println("Error: " + ex.toString());
            return -1;
        }

        final String filter = getNextArg();

        @SuppressWarnings("unchecked")
        final ParceledListSlice<PackageInfo> slice =
                mInterface.getInstalledPackages(getFlags, userId);
        final List<PackageInfo> packages = slice.getList();

        final int count = packages.size();
        for (int p = 0; p < count; p++) {
            final PackageInfo info = packages.get(p);
            if (filter != null && !info.packageName.contains(filter)) {
                continue;
            }
            if (uid != -1 && info.applicationInfo.uid != uid) {
                continue;
            }
            final boolean isSystem =
                    (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0;
            if ((!listDisabled || !info.applicationInfo.enabled) &&
                    (!listEnabled || info.applicationInfo.enabled) &&
                    (!listSystem || isSystem) &&
                    (!listThirdParty || !isSystem)) {
                pw.print("package:");
                if (showSourceDir) {
                    pw.print(info.applicationInfo.sourceDir);
                    pw.print("=");
                }
                pw.print(info.packageName);
                if (showVersionCode) {
                    pw.print(" versionCode:");
                    pw.print(info.applicationInfo.versionCode);
                }
                if (listInstaller) {
                    pw.print("  installer=");
                    pw.print(mInterface.getInstallerPackageName(info.packageName));
                }
                if (showUid) {
                    pw.print(" uid:");
                    pw.print(info.applicationInfo.uid);
                }
                pw.println();
            }
        }
        return 0;
    }

    private int runListPermissionGroups() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final List<PermissionGroupInfo> pgs = mInterface.getAllPermissionGroups(0).getList();

        final int count = pgs.size();
        for (int p = 0; p < count ; p++) {
            final PermissionGroupInfo pgi = pgs.get(p);
            pw.print("permission group:");
            pw.println(pgi.name);
        }
        return 0;
    }

    private int runListPermissions() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        boolean labels = false;
        boolean groups = false;
        boolean userOnly = false;
        boolean summary = false;
        boolean dangerousOnly = false;
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-d":
                    dangerousOnly = true;
                    break;
                case "-f":
                    labels = true;
                    break;
                case "-g":
                    groups = true;
                    break;
                case "-s":
                    groups = true;
                    labels = true;
                    summary = true;
                    break;
                case "-u":
                    userOnly = true;
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        final ArrayList<String> groupList = new ArrayList<String>();
        if (groups) {
            final List<PermissionGroupInfo> infos =
                    mInterface.getAllPermissionGroups(0 /*flags*/).getList();
            final int count = infos.size();
            for (int i = 0; i < count; i++) {
                groupList.add(infos.get(i).name);
            }
            groupList.add(null);
        } else {
            final String grp = getNextArg();
            groupList.add(grp);
        }

        if (dangerousOnly) {
            pw.println("Dangerous Permissions:");
            pw.println("");
            doListPermissions(groupList, groups, labels, summary,
                    PermissionInfo.PROTECTION_DANGEROUS,
                    PermissionInfo.PROTECTION_DANGEROUS);
            if (userOnly) {
                pw.println("Normal Permissions:");
                pw.println("");
                doListPermissions(groupList, groups, labels, summary,
                        PermissionInfo.PROTECTION_NORMAL,
                        PermissionInfo.PROTECTION_NORMAL);
            }
        } else if (userOnly) {
            pw.println("Dangerous and Normal Permissions:");
            pw.println("");
            doListPermissions(groupList, groups, labels, summary,
                    PermissionInfo.PROTECTION_NORMAL,
                    PermissionInfo.PROTECTION_DANGEROUS);
        } else {
            pw.println("All Permissions:");
            pw.println("");
            doListPermissions(groupList, groups, labels, summary,
                    -10000, 10000);
        }
        return 0;
    }

    private Intent parseIntentAndUser() throws URISyntaxException {
        mTargetUser = UserHandle.USER_CURRENT;
        mBrief = false;
        mComponents = false;
        Intent intent = Intent.parseCommandArgs(this, new Intent.CommandOptionHandler() {
            @Override
            public boolean handleOption(String opt, ShellCommand cmd) {
                if ("--user".equals(opt)) {
                    mTargetUser = UserHandle.parseUserArg(cmd.getNextArgRequired());
                    return true;
                } else if ("--brief".equals(opt)) {
                    mBrief = true;
                    return true;
                } else if ("--components".equals(opt)) {
                    mComponents = true;
                    return true;
                }
                return false;
            }
        });
        mTargetUser = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), mTargetUser, false, false, null, null);
        return intent;
    }

    private void printResolveInfo(PrintWriterPrinter pr, String prefix, ResolveInfo ri,
            boolean brief, boolean components) {
        if (brief || components) {
            final ComponentName comp;
            if (ri.activityInfo != null) {
                comp = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            } else if (ri.serviceInfo != null) {
                comp = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
            } else if (ri.providerInfo != null) {
                comp = new ComponentName(ri.providerInfo.packageName, ri.providerInfo.name);
            } else {
                comp = null;
            }
            if (comp != null) {
                if (!components) {
                    pr.println(prefix + "priority=" + ri.priority
                            + " preferredOrder=" + ri.preferredOrder
                            + " match=0x" + Integer.toHexString(ri.match)
                            + " specificIndex=" + ri.specificIndex
                            + " isDefault=" + ri.isDefault);
                }
                pr.println(prefix + comp.flattenToShortString());
                return;
            }
        }
        ri.dump(pr, prefix);
    }

    private int runResolveActivity() {
        Intent intent;
        try {
            intent = parseIntentAndUser();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            ResolveInfo ri = mInterface.resolveIntent(intent, intent.getType(), 0, mTargetUser);
            PrintWriter pw = getOutPrintWriter();
            if (ri == null) {
                pw.println("No activity found");
            } else {
                PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                printResolveInfo(pr, "", ri, mBrief, mComponents);
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Failed calling service", e);
        }
        return 0;
    }

    private int runQueryIntentActivities() {
        Intent intent;
        try {
            intent = parseIntentAndUser();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            List<ResolveInfo> result = mInterface.queryIntentActivities(intent, intent.getType(), 0,
                    mTargetUser).getList();
            PrintWriter pw = getOutPrintWriter();
            if (result == null || result.size() <= 0) {
                pw.println("No activities found");
            } else {
                if (!mComponents) {
                    pw.print(result.size()); pw.println(" activities found:");
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        pw.print("  Activity #"); pw.print(i); pw.println(":");
                        printResolveInfo(pr, "    ", result.get(i), mBrief, mComponents);
                    }
                } else {
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        printResolveInfo(pr, "", result.get(i), mBrief, mComponents);
                    }
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Failed calling service", e);
        }
        return 0;
    }

    private int runQueryIntentServices() {
        Intent intent;
        try {
            intent = parseIntentAndUser();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            List<ResolveInfo> result = mInterface.queryIntentServices(intent, intent.getType(), 0,
                    mTargetUser).getList();
            PrintWriter pw = getOutPrintWriter();
            if (result == null || result.size() <= 0) {
                pw.println("No services found");
            } else {
                if (!mComponents) {
                    pw.print(result.size()); pw.println(" services found:");
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        pw.print("  Service #"); pw.print(i); pw.println(":");
                        printResolveInfo(pr, "    ", result.get(i), mBrief, mComponents);
                    }
                } else {
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        printResolveInfo(pr, "", result.get(i), mBrief, mComponents);
                    }
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Failed calling service", e);
        }
        return 0;
    }

    private int runQueryIntentReceivers() {
        Intent intent;
        try {
            intent = parseIntentAndUser();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            List<ResolveInfo> result = mInterface.queryIntentReceivers(intent, intent.getType(), 0,
                    mTargetUser).getList();
            PrintWriter pw = getOutPrintWriter();
            if (result == null || result.size() <= 0) {
                pw.println("No receivers found");
            } else {
                if (!mComponents) {
                    pw.print(result.size()); pw.println(" receivers found:");
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        pw.print("  Receiver #"); pw.print(i); pw.println(":");
                        printResolveInfo(pr, "    ", result.get(i), mBrief, mComponents);
                    }
                } else {
                    PrintWriterPrinter pr = new PrintWriterPrinter(pw);
                    for (int i = 0; i < result.size(); i++) {
                        printResolveInfo(pr, "", result.get(i), mBrief, mComponents);
                    }
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Failed calling service", e);
        }
        return 0;
    }

    private int runInstall() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final InstallParams params = makeInstallParams();
        final String inPath = getNextArg();

        setParamsSize(params, inPath);
        final int sessionId = doCreateSession(params.sessionParams,
                params.installerPackageName, params.userId);
        boolean abandonSession = true;
        try {
            if (inPath == null && params.sessionParams.sizeBytes == -1) {
                pw.println("Error: must either specify a package size or an APK file");
                return 1;
            }
            if (doWriteSplit(sessionId, inPath, params.sessionParams.sizeBytes, "base.apk",
                    false /*logSuccess*/) != PackageInstaller.STATUS_SUCCESS) {
                return 1;
            }
            if (doCommitSession(sessionId, false /*logSuccess*/)
                    != PackageInstaller.STATUS_SUCCESS) {
                return 1;
            }
            abandonSession = false;
            pw.println("Success");
            return 0;
        } finally {
            if (abandonSession) {
                try {
                    doAbandonSession(sessionId, false /*logSuccess*/);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private int runInstallAbandon() throws RemoteException {
        final int sessionId = Integer.parseInt(getNextArg());
        return doAbandonSession(sessionId, true /*logSuccess*/);
    }

    private int runInstallCommit() throws RemoteException {
        final int sessionId = Integer.parseInt(getNextArg());
        return doCommitSession(sessionId, true /*logSuccess*/);
    }

    private int runInstallCreate() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final InstallParams installParams = makeInstallParams();
        final int sessionId = doCreateSession(installParams.sessionParams,
                installParams.installerPackageName, installParams.userId);

        // NOTE: adb depends on parsing this string
        pw.println("Success: created install session [" + sessionId + "]");
        return 0;
    }

    private int runInstallWrite() throws RemoteException {
        long sizeBytes = -1;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("-S")) {
                sizeBytes = Long.parseLong(getNextArg());
            } else {
                throw new IllegalArgumentException("Unknown option: " + opt);
            }
        }

        final int sessionId = Integer.parseInt(getNextArg());
        final String splitName = getNextArg();
        final String path = getNextArg();
        return doWriteSplit(sessionId, path, sizeBytes, splitName, true /*logSuccess*/);
    }

    private int runInstallRemove() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();

        final int sessionId = Integer.parseInt(getNextArg());

        final String splitName = getNextArg();
        if (splitName == null) {
            pw.println("Error: split name not specified");
            return 1;
        }
        return doRemoveSplit(sessionId, splitName, true /*logSuccess*/);
    }

    private int runInstallExisting() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        int userId = UserHandle.USER_SYSTEM;
        int installFlags = 0;
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--user":
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--ephemeral":
                case "--instant":
                    installFlags |= PackageManager.INSTALL_INSTANT_APP;
                    installFlags &= ~PackageManager.INSTALL_FULL_APP;
                    break;
                case "--full":
                    installFlags &= ~PackageManager.INSTALL_INSTANT_APP;
                    installFlags |= PackageManager.INSTALL_FULL_APP;
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        final String packageName = getNextArg();
        if (packageName == null) {
            pw.println("Error: package name not specified");
            return 1;
        }

        try {
            final int res = mInterface.installExistingPackageAsUser(packageName, userId,
                    installFlags, PackageManager.INSTALL_REASON_UNKNOWN);
            if (res == PackageManager.INSTALL_FAILED_INVALID_URI) {
                throw new NameNotFoundException("Package " + packageName + " doesn't exist");
            }
            pw.println("Package " + packageName + " installed for user: " + userId);
            return 0;
        } catch (RemoteException | NameNotFoundException e) {
            pw.println(e.toString());
            return 1;
        }
    }

    private int runSetInstallLocation() throws RemoteException {
        int loc;

        String arg = getNextArg();
        if (arg == null) {
            getErrPrintWriter().println("Error: no install location specified.");
            return 1;
        }
        try {
            loc = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: install location has to be a number.");
            return 1;
        }
        if (!mInterface.setInstallLocation(loc)) {
            getErrPrintWriter().println("Error: install location has to be a number.");
            return 1;
        }
        return 0;
    }

    private int runGetInstallLocation() throws RemoteException {
        int loc = mInterface.getInstallLocation();
        String locStr = "invalid";
        if (loc == PackageHelper.APP_INSTALL_AUTO) {
            locStr = "auto";
        } else if (loc == PackageHelper.APP_INSTALL_INTERNAL) {
            locStr = "internal";
        } else if (loc == PackageHelper.APP_INSTALL_EXTERNAL) {
            locStr = "external";
        }
        getOutPrintWriter().println(loc + "[" + locStr + "]");
        return 0;
    }

    public int runMovePackage() throws RemoteException {
        final String packageName = getNextArg();
        if (packageName == null) {
            getErrPrintWriter().println("Error: package name not specified");
            return 1;
        }
        String volumeUuid = getNextArg();
        if ("internal".equals(volumeUuid)) {
            volumeUuid = null;
        }

        final int moveId = mInterface.movePackage(packageName, volumeUuid);

        int status = mInterface.getMoveStatus(moveId);
        while (!PackageManager.isMoveStatusFinished(status)) {
            SystemClock.sleep(DateUtils.SECOND_IN_MILLIS);
            status = mInterface.getMoveStatus(moveId);
        }

        if (status == PackageManager.MOVE_SUCCEEDED) {
            getOutPrintWriter().println("Success");
            return 0;
        } else {
            getErrPrintWriter().println("Failure [" + status + "]");
            return 1;
        }
    }

    public int runMovePrimaryStorage() throws RemoteException {
        String volumeUuid = getNextArg();
        if ("internal".equals(volumeUuid)) {
            volumeUuid = null;
        }

        final int moveId = mInterface.movePrimaryStorage(volumeUuid);

        int status = mInterface.getMoveStatus(moveId);
        while (!PackageManager.isMoveStatusFinished(status)) {
            SystemClock.sleep(DateUtils.SECOND_IN_MILLIS);
            status = mInterface.getMoveStatus(moveId);
        }

        if (status == PackageManager.MOVE_SUCCEEDED) {
            getOutPrintWriter().println("Success");
            return 0;
        } else {
            getErrPrintWriter().println("Failure [" + status + "]");
            return 1;
        }
    }

    private int runCompile() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        boolean checkProfiles = SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false);
        boolean forceCompilation = false;
        boolean allPackages = false;
        boolean clearProfileData = false;
        String compilerFilter = null;
        String compilationReason = null;
        String checkProfilesRaw = null;
        boolean secondaryDex = false;
        String split = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-a":
                    allPackages = true;
                    break;
                case "-c":
                    clearProfileData = true;
                    break;
                case "-f":
                    forceCompilation = true;
                    break;
                case "-m":
                    compilerFilter = getNextArgRequired();
                    break;
                case "-r":
                    compilationReason = getNextArgRequired();
                    break;
                case "--check-prof":
                    checkProfilesRaw = getNextArgRequired();
                    break;
                case "--reset":
                    forceCompilation = true;
                    clearProfileData = true;
                    compilationReason = "install";
                    break;
                case "--secondary-dex":
                    secondaryDex = true;
                    break;
                case "--split":
                    split = getNextArgRequired();
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        if (checkProfilesRaw != null) {
            if ("true".equals(checkProfilesRaw)) {
                checkProfiles = true;
            } else if ("false".equals(checkProfilesRaw)) {
                checkProfiles = false;
            } else {
                pw.println("Invalid value for \"--check-prof\". Expected \"true\" or \"false\".");
                return 1;
            }
        }

        if (compilerFilter != null && compilationReason != null) {
            pw.println("Cannot use compilation filter (\"-m\") and compilation reason (\"-r\") " +
                    "at the same time");
            return 1;
        }
        if (compilerFilter == null && compilationReason == null) {
            pw.println("Cannot run without any of compilation filter (\"-m\") and compilation " +
                    "reason (\"-r\") at the same time");
            return 1;
        }

        if (allPackages && split != null) {
            pw.println("-a cannot be specified together with --split");
            return 1;
        }

        if (secondaryDex && split != null) {
            pw.println("--secondary-dex cannot be specified together with --split");
            return 1;
        }

        String targetCompilerFilter;
        if (compilerFilter != null) {
            if (!DexFile.isValidCompilerFilter(compilerFilter)) {
                pw.println("Error: \"" + compilerFilter +
                        "\" is not a valid compilation filter.");
                return 1;
            }
            targetCompilerFilter = compilerFilter;
        } else {
            int reason = -1;
            for (int i = 0; i < PackageManagerServiceCompilerMapping.REASON_STRINGS.length; i++) {
                if (PackageManagerServiceCompilerMapping.REASON_STRINGS[i].equals(
                        compilationReason)) {
                    reason = i;
                    break;
                }
            }
            if (reason == -1) {
                pw.println("Error: Unknown compilation reason: " + compilationReason);
                return 1;
            }
            targetCompilerFilter =
                    PackageManagerServiceCompilerMapping.getCompilerFilterForReason(reason);
        }


        List<String> packageNames = null;
        if (allPackages) {
            packageNames = mInterface.getAllPackages();
        } else {
            String packageName = getNextArg();
            if (packageName == null) {
                pw.println("Error: package name not specified");
                return 1;
            }
            packageNames = Collections.singletonList(packageName);
        }

        List<String> failedPackages = new ArrayList<>();
        int index = 0;
        for (String packageName : packageNames) {
            if (clearProfileData) {
                mInterface.clearApplicationProfileData(packageName);
            }

            if (allPackages) {
                pw.println(++index + "/" + packageNames.size() + ": " + packageName);
                pw.flush();
            }

            boolean result = secondaryDex
                    ? mInterface.performDexOptSecondary(packageName,
                            targetCompilerFilter, forceCompilation)
                    : mInterface.performDexOptMode(packageName,
                            checkProfiles, targetCompilerFilter, forceCompilation,
                            true /* bootComplete */, split);
            if (!result) {
                failedPackages.add(packageName);
            }
        }

        if (failedPackages.isEmpty()) {
            pw.println("Success");
            return 0;
        } else if (failedPackages.size() == 1) {
            pw.println("Failure: package " + failedPackages.get(0) + " could not be compiled");
            return 1;
        } else {
            pw.print("Failure: the following packages could not be compiled: ");
            boolean is_first = true;
            for (String packageName : failedPackages) {
                if (is_first) {
                    is_first = false;
                } else {
                    pw.print(", ");
                }
                pw.print(packageName);
            }
            pw.println();
            return 1;
        }
    }

    private int runreconcileSecondaryDexFiles() throws RemoteException {
        String packageName = getNextArg();
        mInterface.reconcileSecondaryDexFiles(packageName);
        return 0;
    }

    public int runForceDexOpt() throws RemoteException {
        mInterface.forceDexOpt(getNextArgRequired());
        return 0;
    }

    private int runDexoptJob() throws RemoteException {
        String arg;
        List<String> packageNames = new ArrayList<>();
        while ((arg = getNextArg()) != null) {
            packageNames.add(arg);
        }
        boolean result = mInterface.runBackgroundDexoptJob(packageNames.isEmpty() ? null :
                packageNames);
        return result ? 0 : -1;
    }

    private int runDumpProfiles() throws RemoteException {
        String packageName = getNextArg();
        mInterface.dumpProfiles(packageName);
        return 0;
    }

    private int runSnapshotProfile() throws RemoteException {
        PrintWriter pw = getOutPrintWriter();

        // Parse the arguments
        final String packageName = getNextArg();
        final boolean isBootImage = "android".equals(packageName);

        String codePath = null;
        String opt;
        while ((opt = getNextArg()) != null) {
            switch (opt) {
                case "--code-path":
                    if (isBootImage) {
                        pw.write("--code-path cannot be used for the boot image.");
                        return -1;
                    }
                    codePath = getNextArg();
                    break;
                default:
                    pw.write("Unknown arg: " + opt);
                    return -1;
            }
        }

        // If no code path was explicitly requested, select the base code path.
        String baseCodePath = null;
        if (!isBootImage) {
            PackageInfo packageInfo = mInterface.getPackageInfo(packageName, /* flags */ 0,
                    /* userId */0);
            if (packageInfo == null) {
                pw.write("Package not found " + packageName);
                return -1;
            }
            baseCodePath = packageInfo.applicationInfo.getBaseCodePath();
            if (codePath == null) {
                codePath = baseCodePath;
            }
        }

        // Create the profile snapshot.
        final SnapshotRuntimeProfileCallback callback = new SnapshotRuntimeProfileCallback();
        // The calling package is needed to debug permission access.
        final String callingPackage = (Binder.getCallingUid() == Process.ROOT_UID)
                ? "root" : "com.android.shell";
        final int profileType = isBootImage
                ? ArtManager.PROFILE_BOOT_IMAGE : ArtManager.PROFILE_APPS;
        if (!mInterface.getArtManager().isRuntimeProfilingEnabled(profileType, callingPackage)) {
            pw.println("Error: Runtime profiling is not enabled");
            return -1;
        }
        mInterface.getArtManager().snapshotRuntimeProfile(profileType, packageName,
                codePath, callback, callingPackage);
        if (!callback.waitTillDone()) {
            pw.println("Error: callback not called");
            return callback.mErrCode;
        }

        // Copy the snapshot profile to the output profile file.
        try (InputStream inStream = new AutoCloseInputStream(callback.mProfileReadFd)) {
            final String outputFileSuffix = isBootImage || Objects.equals(baseCodePath, codePath)
                    ? "" : ("-" + new File(codePath).getName());
            final String outputProfilePath =
                    ART_PROFILE_SNAPSHOT_DEBUG_LOCATION + packageName + outputFileSuffix + ".prof";
            try (OutputStream outStream = new FileOutputStream(outputProfilePath)) {
                Streams.copy(inStream, outStream);
            }
            // Give read permissions to the other group.
            Os.chmod(outputProfilePath, /*mode*/ 0644 );
        } catch (IOException | ErrnoException e) {
            pw.println("Error when reading the profile fd: " + e.getMessage());
            e.printStackTrace(pw);
            return -1;
        }
        return 0;
    }

    private static class SnapshotRuntimeProfileCallback
            extends ISnapshotRuntimeProfileCallback.Stub {
        private boolean mSuccess = false;
        private int mErrCode = -1;
        private ParcelFileDescriptor mProfileReadFd = null;
        private CountDownLatch mDoneSignal = new CountDownLatch(1);

        @Override
        public void onSuccess(ParcelFileDescriptor profileReadFd) {
            mSuccess = true;
            try {
                // We need to dup the descriptor. We are in the same process as system server
                // and we will be receiving the same object (which will be closed on the
                // server side).
                mProfileReadFd = profileReadFd.dup();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mDoneSignal.countDown();
        }

        @Override
        public void onError(int errCode) {
            mSuccess = false;
            mErrCode = errCode;
            mDoneSignal.countDown();
        }

        boolean waitTillDone() {
            boolean done = false;
            try {
                // The time-out is an arbitrary large value. Since this is a local call the result
                // will come very fast.
                done = mDoneSignal.await(10000000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            return done && mSuccess;
        }
    }

    private int runUninstall() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        int flags = 0;
        int userId = UserHandle.USER_ALL;
        long versionCode = PackageManager.VERSION_CODE_HIGHEST;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-k":
                    flags |= PackageManager.DELETE_KEEP_DATA;
                    break;
                case "--user":
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--versionCode":
                    versionCode = Long.parseLong(getNextArgRequired());
                    break;
                case "--update":
                    flags |= HwPackageManagerService.DELETE_UPDATE_SHARE_APP;
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        final String packageName = getNextArg();
        if (packageName == null) {
            pw.println("Error: package name not specified");
            return 1;
        }

        // if a split is specified, just remove it and not the whole package
        final String splitName = getNextArg();
        if (splitName != null) {
            return runRemoveSplit(packageName, splitName);
        }

        userId = translateUserId(userId, true /*allowAll*/, "runUninstall");
        if (userId == UserHandle.USER_ALL) {
            userId = UserHandle.USER_SYSTEM;
            flags |= PackageManager.DELETE_ALL_USERS;
        } else {
            final PackageInfo info = mInterface.getPackageInfo(packageName,
                    PackageManager.MATCH_STATIC_SHARED_LIBRARIES, userId);
            if (info == null) {
                pw.println("Failure [not installed for " + userId + "]");
                return 1;
            }
            final boolean isSystem =
                    (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // If we are being asked to delete a system app for just one
            // user set flag so it disables rather than reverting to system
            // version of the app.
            if (isSystem) {
                flags |= PackageManager.DELETE_SYSTEM_APP;
            }
        }

        final LocalIntentReceiver receiver = new LocalIntentReceiver();
        mInterface.getPackageInstaller().uninstall(new VersionedPackage(packageName,
                versionCode), null /*callerPackageName*/, flags,
                receiver.getIntentSender(), userId);

        final Intent result = receiver.getResult();
        final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            pw.println("Success");
            return 0;
        } else {
            pw.println("Failure ["
                    + result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + "]");
            return 1;
        }
    }

    private int runRemoveSplit(String packageName, String splitName) throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final SessionParams sessionParams = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
        sessionParams.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
        sessionParams.appPackageName = packageName;
        final int sessionId =
                doCreateSession(sessionParams, null /*installerPackageName*/, UserHandle.USER_ALL);
        boolean abandonSession = true;
        try {
            if (doRemoveSplit(sessionId, splitName, false /*logSuccess*/)
                    != PackageInstaller.STATUS_SUCCESS) {
                return 1;
            }
            if (doCommitSession(sessionId, false /*logSuccess*/)
                    != PackageInstaller.STATUS_SUCCESS) {
                return 1;
            }
            abandonSession = false;
            pw.println("Success");
            return 0;
        } finally {
            if (abandonSession) {
                try {
                    doAbandonSession(sessionId, false /*logSuccess*/);
                } catch (Exception ignore) {
                }
            }
        }
    }

    static class ClearDataObserver extends IPackageDataObserver.Stub {
        boolean finished;
        boolean result;

        @Override
        public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
            synchronized (this) {
                finished = true;
                result = succeeded;
                notifyAll();
            }
        }
    }

    private int runClear() throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;
        String option = getNextOption();
        if (option != null && option.equals("--user")) {
            userId = UserHandle.parseUserArg(getNextArgRequired());
        }

        String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified");
            return 1;
        }

        ClearDataObserver obs = new ClearDataObserver();
        ActivityManager.getService().clearApplicationUserData(pkg, false, obs, userId);
        synchronized (obs) {
            while (!obs.finished) {
                try {
                    obs.wait();
                } catch (InterruptedException e) {
                }
            }
        }

        if (obs.result) {
            getOutPrintWriter().println("Success");
            return 0;
        } else {
            getErrPrintWriter().println("Failed");
            return 1;
        }
    }

    private static String enabledSettingToString(int state) {
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return "default";
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return "enabled";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return "disabled";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return "disabled-user";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return "disabled-until-used";
        }
        return "unknown";
    }

    private int runSetEnabledSetting(int state) throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;
        String option = getNextOption();
        if (option != null && option.equals("--user")) {
            userId = UserHandle.parseUserArg(getNextArgRequired());
        }

        String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package or component specified");
            return 1;
        }
        ComponentName cn = ComponentName.unflattenFromString(pkg);
        if (cn == null) {
            mInterface.setApplicationEnabledSetting(pkg, state, 0, userId,
                    "shell:" + android.os.Process.myUid());
            getOutPrintWriter().println("Package " + pkg + " new state: "
                    + enabledSettingToString(
                    mInterface.getApplicationEnabledSetting(pkg, userId)));
            return 0;
        } else {
            mInterface.setComponentEnabledSetting(cn, state, 0, userId);
            getOutPrintWriter().println("Component " + cn.toShortString() + " new state: "
                    + enabledSettingToString(
                    mInterface.getComponentEnabledSetting(cn, userId)));
            return 0;
        }
    }

    private int runSetHiddenSetting(boolean state) throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;
        String option = getNextOption();
        if (option != null && option.equals("--user")) {
            userId = UserHandle.parseUserArg(getNextArgRequired());
        }

        String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package or component specified");
            return 1;
        }
        mInterface.setApplicationHiddenSettingAsUser(pkg, state, userId);
        getOutPrintWriter().println("Package " + pkg + " new hidden state: "
                + mInterface.getApplicationHiddenSettingAsUser(pkg, userId));
        return 0;
    }

    private int runSuspend(boolean suspendedState) {
        final PrintWriter pw = getOutPrintWriter();
        int userId = UserHandle.USER_SYSTEM;
        String dialogMessage = null;
        final PersistableBundle appExtras = new PersistableBundle();
        final PersistableBundle launcherExtras = new PersistableBundle();
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--user":
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--dialogMessage":
                    dialogMessage = getNextArgRequired();
                    break;
                case "--ael":
                case "--aes":
                case "--aed":
                case "--lel":
                case "--les":
                case "--led":
                    final String key = getNextArgRequired();
                    final String val = getNextArgRequired();
                    if (!suspendedState) {
                        break;
                    }
                    final PersistableBundle bundleToInsert =
                            opt.startsWith("--a") ? appExtras : launcherExtras;
                    switch (opt.charAt(4)) {
                        case 'l':
                            bundleToInsert.putLong(key, Long.valueOf(val));
                            break;
                        case 'd':
                            bundleToInsert.putDouble(key, Double.valueOf(val));
                            break;
                        case 's':
                            bundleToInsert.putString(key, val);
                            break;
                    }
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        final String packageName = getNextArg();
        if (packageName == null) {
            pw.println("Error: package name not specified");
            return 1;
        }
        final String callingPackage =
                (Binder.getCallingUid() == Process.ROOT_UID) ? "root" : "com.android.shell";
        try {
            mInterface.setPackagesSuspendedAsUser(new String[]{packageName}, suspendedState,
                    appExtras, launcherExtras, dialogMessage, callingPackage, userId);
            pw.println("Package " + packageName + " new suspended state: "
                    + mInterface.isPackageSuspendedForUser(packageName, userId));
            return 0;
        } catch (RemoteException | IllegalArgumentException e) {
            pw.println(e.toString());
            return 1;
        }
    }

    private int runGrantRevokePermission(boolean grant) throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;

        String opt = null;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            }
        }

        String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified");
            return 1;
        }
        String perm = getNextArg();
        if (perm == null) {
            getErrPrintWriter().println("Error: no permission specified");
            return 1;
        }

        if (grant) {
            mInterface.grantRuntimePermission(pkg, perm, userId);
        } else {
            mInterface.revokeRuntimePermission(pkg, perm, userId);
        }
        return 0;
    }

    private int runResetPermissions() throws RemoteException {
        mInterface.resetRuntimePermissions();
        return 0;
    }

    private int runSetPermissionEnforced() throws RemoteException {
        final String permission = getNextArg();
        if (permission == null) {
            getErrPrintWriter().println("Error: no permission specified");
            return 1;
        }
        final String enforcedRaw = getNextArg();
        if (enforcedRaw == null) {
            getErrPrintWriter().println("Error: no enforcement specified");
            return 1;
        }
        mInterface.setPermissionEnforced(permission, Boolean.parseBoolean(enforcedRaw));
        return 0;
    }

    private boolean isVendorApp(String pkg) {
        try {
            final PackageInfo info = mInterface.getPackageInfo(pkg, 0, UserHandle.USER_SYSTEM);
            return info != null && info.applicationInfo.isVendor();
        } catch (RemoteException e) {
            return false;
        }
    }

    private boolean isProductApp(String pkg) {
        try {
            final PackageInfo info = mInterface.getPackageInfo(pkg, 0, UserHandle.USER_SYSTEM);
            return info != null && info.applicationInfo.isProduct();
        } catch (RemoteException e) {
            return false;
        }
    }

    private int runGetPrivappPermissions() {
        final String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified.");
            return 1;
        }

        ArraySet<String> privAppPermissions = null;
        if (isVendorApp(pkg)) {
            privAppPermissions = SystemConfig.getInstance().getVendorPrivAppPermissions(pkg);
        } else if (isProductApp(pkg)) {
            privAppPermissions = SystemConfig.getInstance().getProductPrivAppPermissions(pkg);
        } else {
            privAppPermissions = SystemConfig.getInstance().getPrivAppPermissions(pkg);
        }

        getOutPrintWriter().println(privAppPermissions == null
                ? "{}" : privAppPermissions.toString());
        return 0;
    }

    private int runGetPrivappDenyPermissions() {
        final String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified.");
            return 1;
        }

        ArraySet<String> privAppPermissions = null;
        if (isVendorApp(pkg)) {
            privAppPermissions = SystemConfig.getInstance().getVendorPrivAppDenyPermissions(pkg);
        } else if (isProductApp(pkg)) {
            privAppPermissions = SystemConfig.getInstance().getProductPrivAppDenyPermissions(pkg);
        } else {
            privAppPermissions = SystemConfig.getInstance().getPrivAppDenyPermissions(pkg);
        }

        getOutPrintWriter().println(privAppPermissions == null
                ? "{}" : privAppPermissions.toString());
        return 0;
    }

    private int runGetOemPermissions() {
        final String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified.");
            return 1;
        }
        final Map<String, Boolean> oemPermissions = SystemConfig.getInstance()
                .getOemPermissions(pkg);
        if (oemPermissions == null || oemPermissions.isEmpty()) {
            getOutPrintWriter().println("{}");
        } else {
            oemPermissions.forEach((permission, granted) ->
                    getOutPrintWriter().println(permission + " granted:" + granted)
            );
        }
        return 0;
    }

    private String linkStateToString(int state) {
        switch (state) {
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED: return "undefined";
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK: return "ask";
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS: return "always";
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER: return "never";
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK : return "always ask";
        }
        return "Unknown link state: " + state;
    }

    // pm set-app-link [--user USER_ID] PACKAGE {always|ask|always-ask|never|undefined}
    private int runSetAppLink() throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                getErrPrintWriter().println("Error: unknown option: " + opt);
                return 1;
            }
        }

        // Package name to act on; required
        final String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified.");
            return 1;
        }

        // State to apply; {always|ask|never|undefined}, required
        final String modeString = getNextArg();
        if (modeString == null) {
            getErrPrintWriter().println("Error: no app link state specified.");
            return 1;
        }

        final int newMode;
        switch (modeString.toLowerCase()) {
            case "undefined":
                newMode = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
                break;

            case "always":
                newMode = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
                break;

            case "ask":
                newMode = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
                break;

            case "always-ask":
                newMode = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
                break;

            case "never":
                newMode = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
                break;

            default:
                getErrPrintWriter().println("Error: unknown app link state '" + modeString + "'");
                return 1;
        }

        final PackageInfo info = mInterface.getPackageInfo(pkg, 0, userId);
        if (info == null) {
            getErrPrintWriter().println("Error: package " + pkg + " not found.");
            return 1;
        }

        if ((info.applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS) == 0) {
            getErrPrintWriter().println("Error: package " + pkg + " does not handle web links.");
            return 1;
        }

        if (!mInterface.updateIntentVerificationStatus(pkg, newMode, userId)) {
            getErrPrintWriter().println("Error: unable to update app link status for " + pkg);
            return 1;
        }

        return 0;
    }

    // pm get-app-link [--user USER_ID] PACKAGE
    private int runGetAppLink() throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                getErrPrintWriter().println("Error: unknown option: " + opt);
                return 1;
            }
        }

        // Package name to act on; required
        final String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified.");
            return 1;
        }

        final PackageInfo info = mInterface.getPackageInfo(pkg, 0, userId);
        if (info == null) {
            getErrPrintWriter().println("Error: package " + pkg + " not found.");
            return 1;
        }

        if ((info.applicationInfo.privateFlags
                & ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS) == 0) {
            getErrPrintWriter().println("Error: package " + pkg + " does not handle web links.");
            return 1;
        }

        getOutPrintWriter().println(linkStateToString(
                mInterface.getIntentVerificationStatus(pkg, userId)));

        return 0;
    }

    private int runTrimCaches() throws RemoteException {
        String size = getNextArg();
        if (size == null) {
            getErrPrintWriter().println("Error: no size specified");
            return 1;
        }
        long multiplier = 1;
        int len = size.length();
        char c = size.charAt(len - 1);
        if (c < '0' || c > '9') {
            if (c == 'K' || c == 'k') {
                multiplier = 1024L;
            } else if (c == 'M' || c == 'm') {
                multiplier = 1024L*1024L;
            } else if (c == 'G' || c == 'g') {
                multiplier = 1024L*1024L*1024L;
            } else {
                getErrPrintWriter().println("Invalid suffix: " + c);
                return 1;
            }
            size = size.substring(0, len-1);
        }
        long sizeVal;
        try {
            sizeVal = Long.parseLong(size) * multiplier;
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: expected number at: " + size);
            return 1;
        }
        String volumeUuid = getNextArg();
        if ("internal".equals(volumeUuid)) {
            volumeUuid = null;
        }
        ClearDataObserver obs = new ClearDataObserver();
        mInterface.freeStorageAndNotify(volumeUuid, sizeVal,
                StorageManager.FLAG_ALLOCATE_DEFY_ALL_RESERVED, obs);
        synchronized (obs) {
            while (!obs.finished) {
                try {
                    obs.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return 0;
    }

    private static boolean isNumber(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public int runCreateUser() throws RemoteException {
        String name;
        int userId = -1;
        int flags = 0;
        String opt;
        while ((opt = getNextOption()) != null) {
            if ("--profileOf".equals(opt)) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else if ("--managed".equals(opt)) {
                flags |= UserInfo.FLAG_MANAGED_PROFILE;
            } else if ("--restricted".equals(opt)) {
                flags |= UserInfo.FLAG_RESTRICTED;
            } else if ("--ephemeral".equals(opt)) {
                flags |= UserInfo.FLAG_EPHEMERAL;
            } else if ("--guest".equals(opt)) {
                flags |= UserInfo.FLAG_GUEST;
            } else if ("--demo".equals(opt)) {
                flags |= UserInfo.FLAG_DEMO;
            } else {
                getErrPrintWriter().println("Error: unknown option " + opt);
                return 1;
            }
        }
        String arg = getNextArg();
        if (arg == null) {
            getErrPrintWriter().println("Error: no user name specified.");
            return 1;
        }
        name = arg;
        UserInfo info;
        IUserManager um = IUserManager.Stub.asInterface(
                ServiceManager.getService(Context.USER_SERVICE));
        IAccountManager accm = IAccountManager.Stub.asInterface(
                ServiceManager.getService(Context.ACCOUNT_SERVICE));
        if ((flags & UserInfo.FLAG_RESTRICTED) != 0) {
            // In non-split user mode, userId can only be SYSTEM
            int parentUserId = userId >= 0 ? userId : UserHandle.USER_SYSTEM;
            info = um.createRestrictedProfile(name, parentUserId);
            accm.addSharedAccountsFromParentUser(parentUserId, userId,
                    (Process.myUid() == Process.ROOT_UID) ? "root" : "com.android.shell");
        } else if (userId < 0) {
            info = um.createUser(name, flags);
        } else {
            info = um.createProfileForUser(name, flags, userId, null);
        }

        if (info != null) {
            getOutPrintWriter().println("Success: created user id " + info.id);
            return 0;
        } else {
            getErrPrintWriter().println("Error: couldn't create User.");
            return 1;
        }
    }

    public int runRemoveUser() throws RemoteException {
        int userId;
        String arg = getNextArg();
        if (arg == null) {
            getErrPrintWriter().println("Error: no user id specified.");
            return 1;
        }
        userId = UserHandle.parseUserArg(arg);
        IUserManager um = IUserManager.Stub.asInterface(
                ServiceManager.getService(Context.USER_SERVICE));
        if (um.removeUser(userId)) {
            getOutPrintWriter().println("Success: removed user");
            return 0;
        } else {
            getErrPrintWriter().println("Error: couldn't remove user id " + userId);
            return 1;
        }
    }

    public int runSetUserRestriction() throws RemoteException {
        int userId = UserHandle.USER_SYSTEM;
        String opt = getNextOption();
        if (opt != null && "--user".equals(opt)) {
            userId = UserHandle.parseUserArg(getNextArgRequired());
        }

        String restriction = getNextArg();
        String arg = getNextArg();
        boolean value;
        if ("1".equals(arg)) {
            value = true;
        } else if ("0".equals(arg)) {
            value = false;
        } else {
            getErrPrintWriter().println("Error: valid value not specified");
            return 1;
        }
        IUserManager um = IUserManager.Stub.asInterface(
                ServiceManager.getService(Context.USER_SERVICE));
        um.setUserRestriction(restriction, value, userId);
        return 0;
    }

    public int runGetMaxUsers() {
        getOutPrintWriter().println("Maximum supported users: "
                + UserManager.getMaxSupportedUsers());
        return 0;
    }

    public int runGetMaxRunningUsers() {
        ActivityManagerInternal activityManagerInternal =
                LocalServices.getService(ActivityManagerInternal.class);
        getOutPrintWriter().println("Maximum supported running users: "
                + activityManagerInternal.getMaxRunningUsers());
        return 0;
    }

    private static class InstallParams {
        SessionParams sessionParams;
        String installerPackageName;
        int userId = UserHandle.USER_ALL;
    }

    private InstallParams makeInstallParams() {
        final SessionParams sessionParams = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        final InstallParams params = new InstallParams();
        params.sessionParams = sessionParams;
        String opt;
        boolean replaceExisting = true;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-l":
                    sessionParams.installFlags |= PackageManager.INSTALL_FORWARD_LOCK;
                    break;
                case "-r": // ignore
                    break;
                case "-R":
                    replaceExisting = false;
                    break;
                case "-i":
                    params.installerPackageName = getNextArg();
                    if (params.installerPackageName == null) {
                        throw new IllegalArgumentException("Missing installer package");
                    }
                    break;
                case "-t":
                    sessionParams.installFlags |= PackageManager.INSTALL_ALLOW_TEST;
                    break;
                case "-s":
                    sessionParams.installFlags |= PackageManager.INSTALL_EXTERNAL;
                    break;
                case "-f":
                    sessionParams.installFlags |= PackageManager.INSTALL_INTERNAL;
                    break;
                case "-d":
                    sessionParams.installFlags |= PackageManager.INSTALL_ALLOW_DOWNGRADE;
                    break;
                case "-g":
                    sessionParams.installFlags |= PackageManager.INSTALL_GRANT_RUNTIME_PERMISSIONS;
                    break;
                case "--dont-kill":
                    sessionParams.installFlags |= PackageManager.INSTALL_DONT_KILL_APP;
                    break;
                case "--originating-uri":
                    sessionParams.originatingUri = Uri.parse(getNextArg());
                    break;
                case "--referrer":
                    sessionParams.referrerUri = Uri.parse(getNextArg());
                    break;
                case "-p":
                    sessionParams.mode = SessionParams.MODE_INHERIT_EXISTING;
                    sessionParams.appPackageName = getNextArg();
                    if (sessionParams.appPackageName == null) {
                        throw new IllegalArgumentException("Missing inherit package name");
                    }
                    break;
                case "--pkg":
                    sessionParams.appPackageName = getNextArg();
                    if (sessionParams.appPackageName == null) {
                        throw new IllegalArgumentException("Missing package name");
                    }
                    break;
                case "-S":
                    final long sizeBytes = Long.parseLong(getNextArg());
                    if (sizeBytes <= 0) {
                        throw new IllegalArgumentException("Size must be positive");
                    }
                    sessionParams.setSize(sizeBytes);
                    break;
                case "--abi":
                    sessionParams.abiOverride = checkAbiArgument(getNextArg());
                    break;
                case "--ephemeral":
                case "--instant":
                case "--instantapp":
                    sessionParams.setInstallAsInstantApp(true /*isInstantApp*/);
                    break;
                case "--full":
                    sessionParams.setInstallAsInstantApp(false /*isInstantApp*/);
                    break;
                case "--preload":
                    sessionParams.setInstallAsVirtualPreload();
                    break;
                case "--user":
                    params.userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--install-location":
                    sessionParams.installLocation = Integer.parseInt(getNextArg());
                    break;
                case "--force-uuid":
                    sessionParams.installFlags |= PackageManager.INSTALL_FORCE_VOLUME_UUID;
                    sessionParams.volumeUuid = getNextArg();
                    if ("internal".equals(sessionParams.volumeUuid)) {
                        sessionParams.volumeUuid = null;
                    }
                    break;
                case "--force-sdk":
                    sessionParams.installFlags |= PackageManager.INSTALL_FORCE_SDK;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option " + opt);
            }
            if (replaceExisting) {
                sessionParams.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            }
        }
        return params;
    }

    private int runSetHomeActivity() {
        final PrintWriter pw = getOutPrintWriter();
        int userId = UserHandle.USER_SYSTEM;
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--user":
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                default:
                    pw.println("Error: Unknown option: " + opt);
                    return 1;
            }
        }

        String component = getNextArg();
        ComponentName componentName =
                component != null ? ComponentName.unflattenFromString(component) : null;

        if (componentName == null) {
            pw.println("Error: component name not specified or invalid");
            return 1;
        }

        try {
            mInterface.setHomeActivity(componentName, userId);
            pw.println("Success");
            return 0;
        } catch (Exception e) {
            pw.println(e.toString());
            return 1;
        }
    }

    private int runSetInstaller() throws RemoteException {
        final String targetPackage = getNextArg();
        final String installerPackageName = getNextArg();

        if (targetPackage == null || installerPackageName == null) {
            getErrPrintWriter().println("Must provide both target and installer package names");
            return 1;
        }

        mInterface.setInstallerPackageName(targetPackage, installerPackageName);
        getOutPrintWriter().println("Success");
        return 0;
    }

    private int runGetInstantAppResolver() {
        final PrintWriter pw = getOutPrintWriter();
        try {
            final ComponentName instantAppsResolver = mInterface.getInstantAppResolverComponent();
            if (instantAppsResolver == null) {
                return 1;
            }
            pw.println(instantAppsResolver.flattenToString());
            return 0;
        } catch (Exception e) {
            pw.println(e.toString());
            return 1;
        }
    }

    private int runHasFeature() {
        final PrintWriter err = getErrPrintWriter();
        final String featureName = getNextArg();
        if (featureName == null) {
            err.println("Error: expected FEATURE name");
            return 1;
        }
        final String versionString = getNextArg();
        try {
            final int version = (versionString == null) ? 0 : Integer.parseInt(versionString);
            final boolean hasFeature = mInterface.hasSystemFeature(featureName, version);
            getOutPrintWriter().println(hasFeature);
            return hasFeature ? 0 : 1;
        } catch (NumberFormatException e) {
            err.println("Error: illegal version number " + versionString);
            return 1;
        } catch (RemoteException e) {
            err.println(e.toString());
            return 1;
        }
    }

    private int runDump() {
        String pkg = getNextArg();
        if (pkg == null) {
            getErrPrintWriter().println("Error: no package specified");
            return 1;
        }
        ActivityManager.dumpPackageStateStatic(getOutFileDescriptor(), pkg);
        return 0;
    }

    private int runSetHarmfulAppWarning() throws RemoteException {
        int userId = UserHandle.USER_CURRENT;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                getErrPrintWriter().println("Error: Unknown option: " + opt);
                return -1;
            }
        }

        userId = translateUserId(userId, false /*allowAll*/, "runSetHarmfulAppWarning");

        final String packageName = getNextArgRequired();
        final String warning = getNextArg();

        mInterface.setHarmfulAppWarning(packageName, warning, userId);

        return 0;
    }

    private int runGetHarmfulAppWarning() throws RemoteException {
        int userId = UserHandle.USER_CURRENT;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                getErrPrintWriter().println("Error: Unknown option: " + opt);
                return -1;
            }
        }

        userId = translateUserId(userId, false /*allowAll*/, "runGetHarmfulAppWarning");

        final String packageName = getNextArgRequired();
        final CharSequence warning = mInterface.getHarmfulAppWarning(packageName, userId);
        if (!TextUtils.isEmpty(warning)) {
            getOutPrintWriter().println(warning);
            return 0;
        } else {
            return 1;
        }
    }

    private static String checkAbiArgument(String abi) {
        if (TextUtils.isEmpty(abi)) {
            throw new IllegalArgumentException("Missing ABI argument");
        }

        if ("-".equals(abi)) {
            return abi;
        }

        final String[] supportedAbis = Build.SUPPORTED_ABIS;
        for (String supportedAbi : supportedAbis) {
            if (supportedAbi.equals(abi)) {
                return abi;
            }
        }

        throw new IllegalArgumentException("ABI " + abi + " not supported on this device");
    }

    private int translateUserId(int userId, boolean allowAll, String logContext) {
        return ActivityManager.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, allowAll, true, logContext, "pm command");
    }

    private int doCreateSession(SessionParams params, String installerPackageName, int userId)
            throws RemoteException {
        userId = translateUserId(userId, true /*allowAll*/, "runInstallCreate");
        if (userId == UserHandle.USER_ALL) {
            userId = UserHandle.USER_SYSTEM;
            params.installFlags |= PackageManager.INSTALL_ALL_USERS;
        }

        final int sessionId = mInterface.getPackageInstaller()
                .createSession(params, installerPackageName, userId);
        return sessionId;
    }

    private int doWriteSplit(int sessionId, String inPath, long sizeBytes, String splitName,
            boolean logSuccess) throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final ParcelFileDescriptor fd;
        if (STDIN_PATH.equals(inPath)) {
            fd = new ParcelFileDescriptor(getInFileDescriptor());
        } else if (inPath != null) {
            fd = openFileForSystem(inPath, "r");
            if (fd == null) {
                return -1;
            }
            sizeBytes = fd.getStatSize();
            if (sizeBytes < 0) {
                getErrPrintWriter().println("Unable to get size of: " + inPath);
                return -1;
            }
        } else {
            fd = new ParcelFileDescriptor(getInFileDescriptor());
        }
        if (sizeBytes <= 0) {
            getErrPrintWriter().println("Error: must specify a APK size");
            return 1;
        }

        PackageInstaller.Session session = null;
        try {
            session = new PackageInstaller.Session(
                    mInterface.getPackageInstaller().openSession(sessionId));
            session.write(splitName, 0, sizeBytes, fd);

            if (logSuccess) {
                pw.println("Success: streamed " + sizeBytes + " bytes");
            }
            return 0;
        } catch (IOException e) {
            getErrPrintWriter().println("Error: failed to write; " + e.getMessage());
            return 1;
        } finally {
            IoUtils.closeQuietly(session);
        }
    }

    private int doRemoveSplit(int sessionId, String splitName, boolean logSuccess)
            throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        PackageInstaller.Session session = null;
        try {
            session = new PackageInstaller.Session(
                    mInterface.getPackageInstaller().openSession(sessionId));
            session.removeSplit(splitName);

            if (logSuccess) {
                pw.println("Success");
            }
            return 0;
        } catch (IOException e) {
            pw.println("Error: failed to remove split; " + e.getMessage());
            return 1;
        } finally {
            IoUtils.closeQuietly(session);
        }
    }

    private int doCommitSession(int sessionId, boolean logSuccess) throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        PackageInstaller.Session session = null;
        try {
            session = new PackageInstaller.Session(
                    mInterface.getPackageInstaller().openSession(sessionId));

            // Sanity check that all .dm files match an apk.
            // (The installer does not support standalone .dm files and will not process them.)
            try {
                DexMetadataHelper.validateDexPaths(session.getNames());
            } catch (IllegalStateException | IOException e) {
                pw.println("Warning [Could not validate the dex paths: " + e.getMessage() + "]");
            }

            final LocalIntentReceiver receiver = new LocalIntentReceiver();
            session.commit(receiver.getIntentSender());

            final Intent result = receiver.getResult();
            final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                if (logSuccess) {
                    pw.println("Success");
                }
            } else {
                pw.println("Failure ["
                        + result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + "]");
            }
            return status;
        } finally {
            IoUtils.closeQuietly(session);
        }
    }

    private int doAbandonSession(int sessionId, boolean logSuccess) throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        PackageInstaller.Session session = null;
        try {
            session = new PackageInstaller.Session(
                    mInterface.getPackageInstaller().openSession(sessionId));
            session.abandon();
            if (logSuccess) {
                pw.println("Success");
            }
            return 0;
        } finally {
            IoUtils.closeQuietly(session);
        }
    }

    private void doListPermissions(ArrayList<String> groupList, boolean groups, boolean labels,
            boolean summary, int startProtectionLevel, int endProtectionLevel)
                    throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        final int groupCount = groupList.size();
        for (int i = 0; i < groupCount; i++) {
            String groupName = groupList.get(i);
            String prefix = "";
            if (groups) {
                if (i > 0) {
                    pw.println("");
                }
                if (groupName != null) {
                    PermissionGroupInfo pgi =
                            mInterface.getPermissionGroupInfo(groupName, 0 /*flags*/);
                    if (summary) {
                        Resources res = getResources(pgi);
                        if (res != null) {
                            pw.print(loadText(pgi, pgi.labelRes, pgi.nonLocalizedLabel) + ": ");
                        } else {
                            pw.print(pgi.name + ": ");

                        }
                    } else {
                        pw.println((labels ? "+ " : "") + "group:" + pgi.name);
                        if (labels) {
                            pw.println("  package:" + pgi.packageName);
                            Resources res = getResources(pgi);
                            if (res != null) {
                                pw.println("  label:"
                                        + loadText(pgi, pgi.labelRes, pgi.nonLocalizedLabel));
                                pw.println("  description:"
                                        + loadText(pgi, pgi.descriptionRes,
                                                pgi.nonLocalizedDescription));
                            }
                        }
                    }
                } else {
                    pw.println(((labels && !summary) ? "+ " : "") + "ungrouped:");
                }
                prefix = "  ";
            }
            List<PermissionInfo> ps =
                    mInterface.queryPermissionsByGroup(groupList.get(i), 0 /*flags*/).getList();
            final int count = ps.size();
            boolean first = true;
            for (int p = 0 ; p < count ; p++) {
                PermissionInfo pi = ps.get(p);
                if (groups && groupName == null && pi.group != null) {
                    continue;
                }
                final int base = pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                if (base < startProtectionLevel
                        || base > endProtectionLevel) {
                    continue;
                }
                if (summary) {
                    if (first) {
                        first = false;
                    } else {
                        pw.print(", ");
                    }
                    Resources res = getResources(pi);
                    if (res != null) {
                        pw.print(loadText(pi, pi.labelRes,
                                pi.nonLocalizedLabel));
                    } else {
                        pw.print(pi.name);
                    }
                } else {
                    pw.println(prefix + (labels ? "+ " : "")
                            + "permission:" + pi.name);
                    if (labels) {
                        pw.println(prefix + "  package:" + pi.packageName);
                        Resources res = getResources(pi);
                        if (res != null) {
                            pw.println(prefix + "  label:"
                                    + loadText(pi, pi.labelRes,
                                            pi.nonLocalizedLabel));
                            pw.println(prefix + "  description:"
                                    + loadText(pi, pi.descriptionRes,
                                            pi.nonLocalizedDescription));
                        }
                        pw.println(prefix + "  protectionLevel:"
                                + PermissionInfo.protectionToString(pi.protectionLevel));
                    }
                }
            }

            if (summary) {
                pw.println("");
            }
        }
    }

    private String loadText(PackageItemInfo pii, int res, CharSequence nonLocalized)
            throws RemoteException {
        if (nonLocalized != null) {
            return nonLocalized.toString();
        }
        if (res != 0) {
            Resources r = getResources(pii);
            if (r != null) {
                try {
                    return r.getString(res);
                } catch (Resources.NotFoundException e) {
                }
            }
        }
        return null;
    }

    private Resources getResources(PackageItemInfo pii) throws RemoteException {
        Resources res = mResourceCache.get(pii.packageName);
        if (res != null) return res;

        ApplicationInfo ai = mInterface.getApplicationInfo(pii.packageName, 0, 0);
        AssetManager am = new AssetManager();
        am.addAssetPath(ai.publicSourceDir);
        res = new Resources(am, null, null);
        mResourceCache.put(pii.packageName, res);
        return res;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Package manager (package) commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("");
        pw.println("  path [--user USER_ID] PACKAGE");
        pw.println("    Print the path to the .apk of the given PACKAGE.");
        pw.println("");
        pw.println("  dump PACKAGE");
        pw.println("    Print various system state associated with the given PACKAGE.");
        pw.println("");
        pw.println("  list features");
        pw.println("    Prints all features of the system.");
        pw.println("");
        pw.println("  has-feature FEATURE_NAME [version]");
        pw.println("    Prints true and returns exit status 0 when system has a FEATURE_NAME,");
        pw.println("    otherwise prints false and returns exit status 1");
        pw.println("");
        pw.println("  list instrumentation [-f] [TARGET-PACKAGE]");
        pw.println("    Prints all test packages; optionally only those targeting TARGET-PACKAGE");
        pw.println("    Options:");
        pw.println("      -f: dump the name of the .apk file containing the test package");
        pw.println("");
        pw.println("  list libraries");
        pw.println("    Prints all system libraries.");
        pw.println("");
        pw.println("  list packages [-f] [-d] [-e] [-s] [-3] [-i] [-l] [-u] [-U] ");
        pw.println("      [--uid UID] [--user USER_ID] [FILTER]");
        pw.println("    Prints all packages; optionally only those whose name contains");
        pw.println("    the text in FILTER.  Options are:");
        pw.println("      -f: see their associated file");
        pw.println("      -d: filter to only show disabled packages");
        pw.println("      -e: filter to only show enabled packages");
        pw.println("      -s: filter to only show system packages");
        pw.println("      -3: filter to only show third party packages");
        pw.println("      -i: see the installer for the packages");
        pw.println("      -l: ignored (used for compatibility with older releases)");
        pw.println("      -U: also show the package UID");
        pw.println("      -u: also include uninstalled packages");
        pw.println("      --uid UID: filter to only show packages with the given UID");
        pw.println("      --user USER_ID: only list packages belonging to the given user");
        pw.println("");
        pw.println("  list permission-groups");
        pw.println("    Prints all known permission groups.");
        pw.println("");
        pw.println("  list permissions [-g] [-f] [-d] [-u] [GROUP]");
        pw.println("    Prints all known permissions; optionally only those in GROUP.  Options are:");
        pw.println("      -g: organize by group");
        pw.println("      -f: print all information");
        pw.println("      -s: short summary");
        pw.println("      -d: only list dangerous permissions");
        pw.println("      -u: list only the permissions users will see");
        pw.println("");
        pw.println("  resolve-activity [--brief] [--components] [--user USER_ID] INTENT");
        pw.println("    Prints the activity that resolves to the given INTENT.");
        pw.println("");
        pw.println("  query-activities [--brief] [--components] [--user USER_ID] INTENT");
        pw.println("    Prints all activities that can handle the given INTENT.");
        pw.println("");
        pw.println("  query-services [--brief] [--components] [--user USER_ID] INTENT");
        pw.println("    Prints all services that can handle the given INTENT.");
        pw.println("");
        pw.println("  query-receivers [--brief] [--components] [--user USER_ID] INTENT");
        pw.println("    Prints all broadcast receivers that can handle the given INTENT.");
        pw.println("");
        pw.println("  install [-lrtsfdg] [-i PACKAGE] [--user USER_ID|all|current]");
        pw.println("       [-p INHERIT_PACKAGE] [--install-location 0/1/2]");
        pw.println("       [--originating-uri URI] [---referrer URI]");
        pw.println("       [--abi ABI_NAME] [--force-sdk]");
        pw.println("       [--preload] [--instantapp] [--full] [--dont-kill]");
        pw.println("       [--force-uuid internal|UUID] [--pkg PACKAGE] [-S BYTES] [PATH|-]");
        pw.println("    Install an application.  Must provide the apk data to install, either as a");
        pw.println("    file path or '-' to read from stdin.  Options are:");
        pw.println("      -l: forward lock application");
        pw.println("      -R: disallow replacement of existing application");
        pw.println("      -t: allow test packages");
        pw.println("      -i: specify package name of installer owning the app");
        pw.println("      -s: install application on sdcard");
        pw.println("      -f: install application on internal flash");
        pw.println("      -d: allow version code downgrade (debuggable packages only)");
        pw.println("      -p: partial application install (new split on top of existing pkg)");
        pw.println("      -g: grant all runtime permissions");
        pw.println("      -S: size in bytes of package, required for stdin");
        pw.println("      --user: install under the given user.");
        pw.println("      --dont-kill: installing a new feature split, don't kill running app");
        pw.println("      --originating-uri: set URI where app was downloaded from");
        pw.println("      --referrer: set URI that instigated the install of the app");
        pw.println("      --pkg: specify expected package name of app being installed");
        pw.println("      --abi: override the default ABI of the platform");
        pw.println("      --instantapp: cause the app to be installed as an ephemeral install app");
        pw.println("      --full: cause the app to be installed as a non-ephemeral full app");
        pw.println("      --install-location: force the install location:");
        pw.println("          0=auto, 1=internal only, 2=prefer external");
        pw.println("      --force-uuid: force install on to disk volume with given UUID");
        pw.println("      --force-sdk: allow install even when existing app targets platform");
        pw.println("          codename but new one targets a final API level");
        pw.println("");
        pw.println("  install-create [-lrtsfdg] [-i PACKAGE] [--user USER_ID|all|current]");
        pw.println("       [-p INHERIT_PACKAGE] [--install-location 0/1/2]");
        pw.println("       [--originating-uri URI] [---referrer URI]");
        pw.println("       [--abi ABI_NAME] [--force-sdk]");
        pw.println("       [--preload] [--instantapp] [--full] [--dont-kill]");
        pw.println("       [--force-uuid internal|UUID] [--pkg PACKAGE] [-S BYTES]");
        pw.println("    Like \"install\", but starts an install session.  Use \"install-write\"");
        pw.println("    to push data into the session, and \"install-commit\" to finish.");
        pw.println("");
        pw.println("  install-write [-S BYTES] SESSION_ID SPLIT_NAME [PATH|-]");
        pw.println("    Write an apk into the given install session.  If the path is '-', data");
        pw.println("    will be read from stdin.  Options are:");
        pw.println("      -S: size in bytes of package, required for stdin");
        pw.println("");
        pw.println("  install-commit SESSION_ID");
        pw.println("    Commit the given active install session, installing the app.");
        pw.println("");
        pw.println("  install-abandon SESSION_ID");
        pw.println("    Delete the given active install session.");
        pw.println("");
        pw.println("  set-install-location LOCATION");
        pw.println("    Changes the default install location.  NOTE this is only intended for debugging;");
        pw.println("    using this can cause applications to break and other undersireable behavior.");
        pw.println("    LOCATION is one of:");
        pw.println("    0 [auto]: Let system decide the best location");
        pw.println("    1 [internal]: Install on internal device storage");
        pw.println("    2 [external]: Install on external media");
        pw.println("");
        pw.println("  get-install-location");
        pw.println("    Returns the current install location: 0, 1 or 2 as per set-install-location.");
        pw.println("");
        pw.println("  move-package PACKAGE [internal|UUID]");
        pw.println("");
        pw.println("  move-primary-storage [internal|UUID]");
        pw.println("");
        pw.println("  pm uninstall [-k] [--user USER_ID] [--versionCode VERSION_CODE] PACKAGE [SPLIT]");
        pw.println("    Remove the given package name from the system.  May remove an entire app");
        pw.println("    if no SPLIT name is specified, otherwise will remove only the split of the");
        pw.println("    given app.  Options are:");
        pw.println("      -k: keep the data and cache directories around after package removal.");
        pw.println("      --user: remove the app from the given user.");
        pw.println("      --versionCode: only uninstall if the app has the given version code.");
        pw.println("");
        pw.println("  clear [--user USER_ID] PACKAGE");
        pw.println("    Deletes all data associated with a package.");
        pw.println("");
        pw.println("  enable [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("  disable [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("  disable-user [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("  disable-until-used [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("  default-state [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("    These commands change the enabled state of a given package or");
        pw.println("    component (written as \"package/class\").");
        pw.println("");
        pw.println("  hide [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("  unhide [--user USER_ID] PACKAGE_OR_COMPONENT");
        pw.println("");
        pw.println("  suspend [--user USER_ID] TARGET-PACKAGE");
        pw.println("    Suspends the specified package (as user).");
        pw.println("");
        pw.println("  unsuspend [--user USER_ID] TARGET-PACKAGE");
        pw.println("    Unsuspends the specified package (as user).");
        pw.println("");
        pw.println("  grant [--user USER_ID] PACKAGE PERMISSION");
        pw.println("  revoke [--user USER_ID] PACKAGE PERMISSION");
        pw.println("    These commands either grant or revoke permissions to apps.  The permissions");
        pw.println("    must be declared as used in the app's manifest, be runtime permissions");
        pw.println("    (protection level dangerous), and the app targeting SDK greater than Lollipop MR1.");
        pw.println("");
        pw.println("  reset-permissions");
        pw.println("    Revert all runtime permissions to their default state.");
        pw.println("");
        pw.println("  set-permission-enforced PERMISSION [true|false]");
        pw.println("");
        pw.println("  get-privapp-permissions TARGET-PACKAGE");
        pw.println("    Prints all privileged permissions for a package.");
        pw.println("");
        pw.println("  get-privapp-deny-permissions TARGET-PACKAGE");
        pw.println("    Prints all privileged permissions that are denied for a package.");
        pw.println("");
        pw.println("  get-oem-permissions TARGET-PACKAGE");
        pw.println("    Prints all OEM permissions for a package.");
        pw.println("");
        pw.println("  set-app-link [--user USER_ID] PACKAGE {always|ask|never|undefined}");
        pw.println("  get-app-link [--user USER_ID] PACKAGE");
        pw.println("");
        pw.println("  trim-caches DESIRED_FREE_SPACE [internal|UUID]");
        pw.println("    Trim cache files to reach the given free space.");
        pw.println("");
        pw.println("  create-user [--profileOf USER_ID] [--managed] [--restricted] [--ephemeral]");
        pw.println("      [--guest] USER_NAME");
        pw.println("    Create a new user with the given USER_NAME, printing the new user identifier");
        pw.println("    of the user.");
        pw.println("");
        pw.println("  remove-user USER_ID");
        pw.println("    Remove the user with the given USER_IDENTIFIER, deleting all data");
        pw.println("    associated with that user");
        pw.println("");
        pw.println("  set-user-restriction [--user USER_ID] RESTRICTION VALUE");
        pw.println("");
        pw.println("  get-max-users");
        pw.println("");
        pw.println("  get-max-running-users");
        pw.println("");
        pw.println("  compile [-m MODE | -r REASON] [-f] [-c] [--split SPLIT_NAME]");
        pw.println("          [--reset] [--check-prof (true | false)] (-a | TARGET-PACKAGE)");
        pw.println("    Trigger compilation of TARGET-PACKAGE or all packages if \"-a\".  Options are:");
        pw.println("      -a: compile all packages");
        pw.println("      -c: clear profile data before compiling");
        pw.println("      -f: force compilation even if not needed");
        pw.println("      -m: select compilation mode");
        pw.println("          MODE is one of the dex2oat compiler filters:");
        pw.println("            assume-verified");
        pw.println("            extract");
        pw.println("            verify");
        pw.println("            quicken");
        pw.println("            space-profile");
        pw.println("            space");
        pw.println("            speed-profile");
        pw.println("            speed");
        pw.println("            everything");
        pw.println("      -r: select compilation reason");
        pw.println("          REASON is one of:");
        for (int i = 0; i < PackageManagerServiceCompilerMapping.REASON_STRINGS.length; i++) {
            pw.println("            " + PackageManagerServiceCompilerMapping.REASON_STRINGS[i]);
        }
        pw.println("      --reset: restore package to its post-install state");
        pw.println("      --check-prof (true | false): look at profiles when doing dexopt?");
        pw.println("      --secondary-dex: compile app secondary dex files");
        pw.println("      --split SPLIT: compile only the given split name");
        pw.println("");
        pw.println("  force-dex-opt PACKAGE");
        pw.println("    Force immediate execution of dex opt for the given PACKAGE.");
        pw.println("");
        pw.println("  bg-dexopt-job");
        pw.println("    Execute the background optimizations immediately.");
        pw.println("    Note that the command only runs the background optimizer logic. It may");
        pw.println("    overlap with the actual job but the job scheduler will not be able to");
        pw.println("    cancel it. It will also run even if the device is not in the idle");
        pw.println("    maintenance mode.");
        pw.println("");
        pw.println("  reconcile-secondary-dex-files TARGET-PACKAGE");
        pw.println("    Reconciles the package secondary dex files with the generated oat files.");
        pw.println("");
        pw.println("  dump-profiles TARGET-PACKAGE");
        pw.println("    Dumps method/class profile files to");
        pw.println("    " + ART_PROFILE_SNAPSHOT_DEBUG_LOCATION + "TARGET-PACKAGE.txt");
        pw.println("");
        pw.println("  snapshot-profile TARGET-PACKAGE [--code-path path]");
        pw.println("    Take a snapshot of the package profiles to");
        pw.println("    " + ART_PROFILE_SNAPSHOT_DEBUG_LOCATION
                + "TARGET-PACKAGE[-code-path].prof");
        pw.println("    If TARGET-PACKAGE=android it will take a snapshot of the boot image");
        pw.println("");
        pw.println("  set-home-activity [--user USER_ID] TARGET-COMPONENT");
        pw.println("    Set the default home activity (aka launcher).");
        pw.println("");
        pw.println("  set-installer PACKAGE INSTALLER");
        pw.println("    Set installer package name");
        pw.println("");
        pw.println("  get-instantapp-resolver");
        pw.println("    Return the name of the component that is the current instant app installer.");
        pw.println("");
        pw.println("  set-harmful-app-warning [--user <USER_ID>] <PACKAGE> [<WARNING>]");
        pw.println("    Mark the app as harmful with the given warning message.");
        pw.println("");
        pw.println("  get-harmful-app-warning [--user <USER_ID>] <PACKAGE>");
        pw.println("    Return the harmful app warning message for the given app, if present");
        pw.println();
        pw.println("  uninstall-system-updates");
        pw.println("    Remove updates to all system applications and fall back to their /system " +
                "version.");
        pw.println();
        mHwPackageManagerShellCommand.onHelp(pw);
        Intent.printIntentArgsHelp(pw , "");
    }

    private static class LocalIntentReceiver {
        private final SynchronousQueue<Intent> mResult = new SynchronousQueue<>();

        private IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
                    IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                try {
                    mResult.offer(intent, 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        public IntentSender getIntentSender() {
            return new IntentSender((IIntentSender) mLocalSender);
        }

        public Intent getResult() {
            try {
                return mResult.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
