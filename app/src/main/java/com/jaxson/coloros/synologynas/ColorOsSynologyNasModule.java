package com.jaxson.coloros.synologynas;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.jaxson.coloros.synologynas.backup.SharedPreferencesBackupHashStore;
import com.jaxson.coloros.synologynas.backup.SynologyBackupRepository;
import com.jaxson.coloros.synologynas.gallery.ColorOsGalleryBridge;
import com.jaxson.coloros.synologynas.gallery.ColorOsNasProviderProxy;
import com.jaxson.coloros.synologynas.gallery.GalleryBackupClient;
import com.jaxson.coloros.synologynas.gallery.GalleryContract;
import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;
import com.jaxson.coloros.synologynas.gallery.RemoteGalleryRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.error.HookFailedError;

public final class ColorOsSynologyNasModule extends XposedModule {
    private static final String TAG = "ColorOSSynologyNAS";
    private static final String MODULE_PACKAGE = "com.jaxson.coloros.synologynas";
    private static final String MODULE_ACTIVITY = MODULE_PACKAGE + ".MainActivity";
    private static final String CONFIG_WRAPPER = "com.oplus.aiunit.vision.hda";
    private static final String NAS_ENTRY_STARTER = "com.oplus.aiunit.vision.goq";
    private static final String NAS_DIAGNOSE_PAGE =
            "com.oplus.gallery.basebiz.track.NasConnectionDiagnoseTrackHelper$DiagnosePage";
    private static final String FOLDER_NOTE_CONFIG = "com.oplus.aiunit.vision.bug";
    private static final String NAS_SYNC_STATE_INFO = "com.oplus.aiunit.vision.zcq";
    private static final String CLOUD_SYNC_PROXY =
            "com.oplus.gallery.framework.abilities.cloudsync.CloudSyncProxyDM";
    private static final String NAS_DEVICE_MANAGER = "com.oplus.aiunit.vision.ahq";
    private static final String NAS_DEVICE_DAO = "com.oplus.aiunit.vision.mgq";
    private static final String NAS_PRELOAD_HELPER = "com.oplus.aiunit.vision.ynq";
    private static final String NAS_IMPL = "com.oplus.aiunit.vision.alq";
    private static final String NAS_DEVICE_OFFLINE_BINDING = "com.oplus.aiunit.vision.ehq";
    private static final String COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope";
    private static final String FUNCTION_ONE = "kotlin.jvm.functions.Function1";
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    private static final String NAS_DOWNLOAD_HANDLE = "com.oplus.aiunit.vision.z8g";
    private static final String MAIN_TAB_ALBUM_SET_MODEL = "com.oplus.aiunit.vision.enn";
    private static final String NAS_ALBUMS_VIEW_BINDING = "com.oplus.aiunit.vision.h9q";
    private static final String NAS_ALBUMS_VIEW_DATA = "com.oplus.aiunit.vision.mjq";
    private static final String VIEW_DATA_BASE = "com.oplus.aiunit.vision.wfb0";
    private static final String BASE_VIEW_HOLDER =
            "com.oplus.gallery.standard_lib.baselist.view.BaseViewHolder";
    private static final String MULTI_DELETE_DIALOG = "com.oplus.aiunit.vision.hfq$b";
    private static final String SINGLE_DELETE_DIALOG = "com.oplus.aiunit.vision.ijw$b";
    private static final String NAS_DELETE_PARAMS = "com.oplus.aiunit.vision.gfq$a";
    private static final String MEDIA_PATH = "com.oplus.aiunit.vision.dst";
    private static final String MEDIA_OBJECT_RESOLVER = "com.oplus.aiunit.vision.q7b";
    private static final String NAS_MEDIA_ITEM = "com.oplus.aiunit.vision.jlq";
    private static final String DIALOG_BUILDER =
            "com.coui.appcompat.dialog.COUIAlertDialogBuilder";

    private String processName;
    private boolean attachHookInstalled;
    private boolean targetHooksInstalled;
    private volatile String currentDeviceModel = GalleryContract.DEFAULT_DEVICE_MODEL;
    private volatile boolean currentNasConnected;
    private volatile int currentPhotoCount;
    private volatile boolean hasStoredSynologyMetadata;
    private final ExecutorService nasStatusExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ColorOSSynologyNAS-status");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean nasStatusRefreshInFlight = new AtomicBoolean();
    private final ThreadLocal<Integer> deleteMessageSuppressionDepth = new ThreadLocal<>();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public synchronized void onPackageReady(PackageReadyParam param) {
        if (attachHookInstalled
                || !param.isFirstPackage()
                || !HookPolicy.TARGET_PACKAGE.equals(param.getPackageName())
                || !HookPolicy.TARGET_PACKAGE.equals(processName)) {
            return;
        }

        try {
            hookApplicationAttach(param.getClassLoader());
            attachHookInstalled = true;
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "Application.attach resolution failed", error);
        } catch (HookFailedError | RuntimeException error) {
            log(Log.ERROR, TAG, "Application.attach hook failed", error);
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private void hookApplicationAttach(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        attach.setAccessible(true);
        hook(attach).intercept(chain -> {
            Context context = (Context) chain.getArg(0);
            installTargetHooks(context, context.getClassLoader());
            return chain.proceed();
        });
    }

    private synchronized void installTargetHooks(Context context, ClassLoader classLoader) {
        if (targetHooksInstalled || !isSupportedPackageVersion(context)) {
            return;
        }

        try {
            SharedPreferences remotePreferences = getRemotePreferences(RemoteConfigStore.GROUP);
            RemoteConfigStore remoteConfigStore = new RemoteConfigStore(remotePreferences);
            GalleryRemoteClient remoteClient = new GalleryRemoteClient(
                    new RemoteGalleryRepository(remoteConfigStore)
            );
            GalleryRemoteClient statusClient = new GalleryRemoteClient(
                    new RemoteGalleryRepository(remoteConfigStore)
            );
            GalleryBackupClient backupClient = new GalleryBackupClient(
                    new SynologyBackupRepository(
                            remoteConfigStore,
                            new SharedPreferencesBackupHashStore(
                                    context.getSharedPreferences(
                                            SharedPreferencesBackupHashStore.PREFERENCES_NAME,
                                            Context.MODE_PRIVATE
                                    )
                            )
                    )
            );
            initializeNasState(remoteClient);
            HookTargets targets = resolveHookTargets(classLoader);
            installFeatureHooks(targets);
            installDeleteDialogHooks(targets);
            installProviderHooks(
                    classLoader,
                    targets,
                    remoteClient,
                    statusClient,
                    backupClient
            );
            installNasPreloadHook(targets);
            installGalleryHomeHook(classLoader, targets, remoteClient);
            installEntryHook(targets, remoteClient);
            installSyntheticDevicePresenceHook(targets);
            installLabelHook(targets);
            installGalleryCardHooks(
                    context,
                    getModuleApplicationInfo(),
                    targets,
                    remoteClient
            );
            targetHooksInstalled = true;
            logInfo("remote DSM configuration available: " + remoteClient.isConfigured());
            logInfo("hooks installed for gallery version " + HookPolicy.TARGET_VERSION_CODE);
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "hook target resolution failed", error);
        } catch (HookFailedError | RuntimeException error) {
            log(Log.ERROR, TAG, "hook installation failed", error);
        }
    }

    private boolean isSupportedPackageVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(HookPolicy.TARGET_PACKAGE, 0);
            long versionCode = packageInfo.getLongVersionCode();
            boolean supported = HookPolicy.isSupportedTarget(
                    context.getPackageName(),
                    processName,
                    versionCode
            );
            if (!supported) {
                logInfo("unsupported gallery target; hooks not installed: package="
                        + context.getPackageName() + ", process=" + processName
                        + ", versionCode=" + versionCode);
            }
            return supported;
        } catch (PackageManager.NameNotFoundException error) {
            log(Log.ERROR, TAG, "gallery package lookup failed", error);
            return false;
        }
    }

    private HookTargets resolveHookTargets(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> diagnosePage = Class.forName(NAS_DIAGNOSE_PAGE, false, classLoader);
        Class<?> nasProvider = Class.forName(NAS_PROVIDER, false, classLoader);
        Class<?> cloudSyncProxy = Class.forName(CLOUD_SYNC_PROXY, false, classLoader);
        Class<?> coroutineScope = Class.forName(COROUTINE_SCOPE, false, classLoader);
        Class<?> functionOne = Class.forName(FUNCTION_ONE, false, classLoader);
        Class<?> nasDeviceAvailability = Class.forName(
                "com.oplus.gallery.business_lib.nas.NasDeviceAvailability",
                false,
                classLoader
        );
        Class<?> baseViewHolder = Class.forName(BASE_VIEW_HOLDER, false, classLoader);
        Class<?> viewDataBase = Class.forName(VIEW_DATA_BASE, false, classLoader);
        Class<?> nasPreloadHelper = Class.forName(NAS_PRELOAD_HELPER, false, classLoader);
        Class<?> deleteParams = Class.forName(NAS_DELETE_PARAMS, false, classLoader);
        Class<?> mediaPath = Class.forName(MEDIA_PATH, false, classLoader);
        Class<?> dialogBuilder = Class.forName(DIALOG_BUILDER, false, classLoader);
        Constructor<?> cloudSyncProxyConstructor = cloudSyncProxy.getDeclaredConstructor();
        cloudSyncProxyConstructor.setAccessible(true);
        return new HookTargets(
                findDeclaredMethod(
                        classLoader,
                        CONFIG_WRAPPER,
                        "c",
                        String.class,
                        boolean.class,
                        boolean.class
                ),
                findDeclaredMethod(
                        classLoader,
                        CONFIG_WRAPPER,
                        "d",
                        int.class,
                        String.class,
                        boolean.class
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_ENTRY_STARTER,
                        "a",
                        Context.class,
                        String.class,
                        diagnosePage,
                        int.class
                ),
                findDeclaredMethod(classLoader, FOLDER_NOTE_CONFIG, "w", long.class),
                findDeclaredMethod(classLoader, NAS_SYNC_STATE_INFO, "m", Context.class),
                cloudSyncProxyConstructor,
                findDeclaredMethod(classLoader, NAS_DEVICE_MANAGER, "b"),
                findDeclaredMethod(classLoader, NAS_DEVICE_DAO, "f", String.class),
                findDeclaredMethod(
                        classLoader,
                        NAS_PRELOAD_HELPER,
                        "a",
                        int.class,
                        nasPreloadHelper,
                        ArrayList.class,
                        CountDownLatch.class
                ),
                findDeclaredMethod(classLoader, NAS_IMPL, "h", nasProvider, String.class),
                findDeclaredMethod(classLoader, NAS_DOWNLOAD_HANDLE, "cancel"),
                findDeclaredMethod(classLoader, MAIN_TAB_ALBUM_SET_MODEL, "i"),
                findDeclaredMethod(
                        classLoader,
                        NAS_ALBUMS_VIEW_BINDING,
                        "e",
                        baseViewHolder,
                        int.class,
                        viewDataBase
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_ALBUMS_VIEW_BINDING,
                        "l",
                        Integer.class,
                        nasDeviceAvailability
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_DEVICE_OFFLINE_BINDING,
                        "b",
                        coroutineScope,
                        boolean.class,
                        String.class,
                        functionOne
                ),
                functionOne.getMethod("invoke", Object.class),
                findDeclaredMethod(classLoader, MULTI_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredField(classLoader, MULTI_DELETE_DIALOG, "$params"),
                findDeclaredField(deleteParams, "a"),
                findDeclaredMethod(classLoader, SINGLE_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredField(classLoader, SINGLE_DELETE_DIALOG, "$itemPath"),
                findDeclaredMethod(classLoader, MEDIA_PATH, "b", String.class),
                findDeclaredMethod(classLoader, MEDIA_OBJECT_RESOLVER, "f", mediaPath),
                findDeclaredField(classLoader, NAS_MEDIA_ITEM, "F0"),
                findDeclaredMethod(classLoader, NAS_MEDIA_ITEM, "i0"),
                findDeclaredMethodReturning(
                        dialogBuilder,
                        "setMessage",
                        dialogBuilder,
                        CharSequence.class
                )
        );
    }

    private Method findDeclaredMethod(
            ClassLoader classLoader,
            String className,
            String methodName,
            Class<?>... parameterTypes
    ) throws ReflectiveOperationException {
        Class<?> targetClass = Class.forName(className, false, classLoader);
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private Field findDeclaredField(ClassLoader classLoader, String className, String fieldName)
            throws ReflectiveOperationException {
        return findDeclaredField(Class.forName(className, false, classLoader), fieldName);
    }

    private Field findDeclaredField(Class<?> targetClass, String fieldName)
            throws ReflectiveOperationException {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private Method findDeclaredMethodReturning(
            Class<?> targetClass,
            String methodName,
            Class<?> returnType,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        for (Method method : targetClass.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())
                    || !returnType.equals(method.getReturnType())) {
                continue;
            }
            Class<?>[] actualParameterTypes = method.getParameterTypes();
            if (actualParameterTypes.length != parameterTypes.length) {
                continue;
            }
            boolean parametersMatch = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!parameterTypes[index].equals(actualParameterTypes[index])) {
                    parametersMatch = false;
                    break;
                }
            }
            if (parametersMatch) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
                targetClass.getName() + "." + methodName + " with requested return type"
        );
    }

    private void installFeatureHooks(HookTargets targets) {
        hook(targets.readBoolean).intercept(chain -> {
            String configId = (String) chain.getArg(0);
            if (HookPolicy.shouldForceFeature(configId)) {
                return true;
            }
            return chain.proceed();
        });
        hook(targets.readBooleanDefault).intercept(chain -> {
            String configId = (String) chain.getArg(1);
            if (HookPolicy.shouldForceFeature(configId)) {
                return true;
            }
            return chain.proceed();
        });
    }

    private void installDeleteDialogHooks(HookTargets targets) {
        hook(targets.showMultiDeleteDialog).intercept(chain -> {
            String deviceUserId;
            try {
                Object params = targets.multiDeleteParams.get(chain.getThisObject());
                deviceUserId = (String) targets.deleteParamsDeviceId.get(params);
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "multi-select delete dialog device lookup failed", error);
                return chain.proceed();
            }
            return interceptDeleteDialog(chain, deviceUserId);
        });

        hook(targets.showSingleDeleteDialog).intercept(chain -> {
            String deviceUserId;
            try {
                deviceUserId = resolveSingleDeleteDeviceId(targets, chain.getThisObject());
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "single-photo delete dialog device lookup failed", error);
                return chain.proceed();
            }
            return interceptDeleteDialog(chain, deviceUserId);
        });

        hook(targets.setDialogMessage).intercept(chain -> {
            Integer suppressionDepth = deleteMessageSuppressionDepth.get();
            if (suppressionDepth == null || suppressionDepth == 0) {
                return chain.proceed();
            }
            logInfo("removed FEINIU recycle-bin copy from Synology delete dialog");
            return chain.getThisObject();
        });
    }

    private String resolveSingleDeleteDeviceId(HookTargets targets, Object dialogCoroutine)
            throws ReflectiveOperationException {
        String itemPath = (String) targets.singleDeleteItemPath.get(dialogCoroutine);
        Object mediaPath = targets.parseMediaPath.invoke(null, itemPath);
        Object mediaItem = targets.resolveMediaObject.invoke(null, mediaPath);
        if (!targets.nasMediaDeviceId.getDeclaringClass().isInstance(mediaItem)) {
            return null;
        }
        String deviceUserId = (String) targets.nasMediaDeviceId.get(mediaItem);
        if (deviceUserId == null || deviceUserId.isEmpty()) {
            targets.loadNasMediaMetadata.invoke(mediaItem);
            deviceUserId = (String) targets.nasMediaDeviceId.get(mediaItem);
        }
        return deviceUserId;
    }

    private Object interceptDeleteDialog(XposedInterface.Chain chain, String deviceUserId)
            throws Throwable {
        if (!HookPolicy.shouldSuppressSynologyDeleteMessage(deviceUserId)) {
            return chain.proceed();
        }
        Integer currentDepth = deleteMessageSuppressionDepth.get();
        int previousDepth = currentDepth == null ? 0 : currentDepth;
        deleteMessageSuppressionDepth.set(previousDepth + 1);
        try {
            return chain.proceed();
        } finally {
            if (previousDepth == 0) {
                deleteMessageSuppressionDepth.remove();
            } else {
                deleteMessageSuppressionDepth.set(previousDepth);
            }
        }
    }

    private void installGalleryHomeHook(
            ClassLoader classLoader,
            HookTargets targets,
            GalleryRemoteClient remoteClient
    ) {
        hook(targets.populateMainTabAlbumGroups).intercept(chain -> {
            Object result = chain.proceed();
            if (!remoteClient.isConfigured()) {
                return result;
            }
            NasState nasState = currentNasState();
            if (ColorOsGalleryBridge.ensureSynologyHomeEntry(
                    chain.getThisObject(),
                    classLoader,
                    nasState.deviceModel(),
                    nasState.connected()
            )) {
                logInfo("injected Synology private cloud album into ColorOS gallery home");
            }
            return result;
        });
    }

    private void installEntryHook(HookTargets targets, GalleryRemoteClient remoteClient) {
        hook(targets.openNasDeviceSpace).intercept(chain -> {
            Context context = (Context) chain.getArg(0);
            String deviceUserId = (String) chain.getArg(1);
            if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                return chain.proceed();
            }
            int actionFlags = (int) chain.getArg(3);
            if (remoteClient.isConfigured()
                    && !HookPolicy.shouldOpenSynologyDeviceManager(actionFlags)) {
                return null;
            }
            Intent intent = new Intent()
                    .setComponent(new ComponentName(MODULE_PACKAGE, MODULE_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_DEVICE_USER_ID, deviceUserId);
            context.startActivity(intent);
            logInfo(HookPolicy.shouldOpenSynologyDeviceManager(actionFlags)
                    ? "opened Synology device management from ColorOS gallery"
                    : "opened Synology configuration for offline synthetic device");
            return null;
        });
    }

    private void installProviderHooks(
            ClassLoader classLoader,
            HookTargets targets,
            GalleryRemoteClient remoteClient,
            GalleryRemoteClient statusClient,
            GalleryBackupClient backupClient
    ) throws ReflectiveOperationException {
        Object nasStatusFlow = ColorOsGalleryBridge.mutableStatusFlow(
                classLoader,
                currentNasConnected
        );
        hook(targets.cloudSyncProxyConstructor).intercept(chain -> {
            Object result = chain.proceed();
            ColorOsGalleryBridge.replaceProvider(
                    chain.getThisObject(),
                    classLoader,
                    remoteClient,
                    backupClient,
                    () -> currentPhotoCount
            );
            logInfo("replaced FEINIU provider with Synology DSM 7 provider");
            return result;
        });

        hook(targets.listNasDevices).intercept(chain -> {
            Object result = chain.proceed();
            if (!(result instanceof ArrayList<?> devices)
                    || !ColorOsGalleryBridge.isConfiguredManager(chain.getThisObject())) {
                return result;
            }
            ArrayList<Object> devicesWithSynology = ColorOsGalleryBridge.withSynologyDevice(
                    devices,
                    classLoader,
                    currentDeviceModel,
                    currentNasConnected
            );
            logInfo("injected Synology DSM 7 device into ColorOS gallery");
            return devicesWithSynology;
        });

        hook(targets.readGalleryStats).intercept(chain -> {
            String deviceUserId = (String) chain.getArg(0);
            if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                return chain.proceed();
            }
            Object result = chain.proceed();
            Integer storedPhotoCount = ColorOsGalleryBridge.photoCount(result);
            if (storedPhotoCount != null) {
                currentPhotoCount = storedPhotoCount;
                hasStoredSynologyMetadata = true;
            }
            return result;
        });

        hook(targets.observeNasStatus).intercept(chain -> {
            String deviceUserId = (String) chain.getArg(1);
            if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                return chain.proceed();
            }
            refreshNasStateAsync(
                    classLoader,
                    statusClient,
                    nasStatusFlow
            );
            return nasStatusFlow;
        });

        hook(targets.cancelNasDownload).intercept(chain -> {
            if (ColorOsNasProviderProxy.shouldSuppressCancel(chain.getThisObject())) {
                return null;
            }
            return chain.proceed();
        });
    }

    private void installNasPreloadHook(HookTargets targets) {
        hook(targets.preloadNasMetadata).intercept(chain -> {
            int currentIndex = (int) chain.getArg(0);
            @SuppressWarnings("unchecked")
            ArrayList<Object> devices = (ArrayList<Object>) chain.getArg(2);
            if (currentIndex >= devices.size()) {
                return chain.proceed();
            }

            int nextIndex;
            try {
                int nextIndexIfStored = ColorOsGalleryBridge.nextPreloadIndex(
                        devices,
                        currentIndex,
                        true
                );
                if (nextIndexIfStored == currentIndex) {
                    return chain.proceed();
                }
                boolean hasStoredMetadata = hasStoredSynologyMetadata
                        || readStoredSynologyMetadata(targets);
                nextIndex = ColorOsGalleryBridge.nextPreloadIndex(
                        devices,
                        currentIndex,
                        hasStoredMetadata
                );
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "stored Synology metadata lookup failed", error);
                return chain.proceed();
            }
            if (nextIndex == currentIndex) {
                return chain.proceed();
            }

            logInfo("skipped Synology startup metadata preload; local photo count="
                    + currentPhotoCount);
            if (nextIndex >= devices.size()) {
                CountDownLatch completion = (CountDownLatch) chain.getArg(3);
                completion.countDown();
                return null;
            }

            Object[] args = chain.getArgs().toArray();
            args[0] = nextIndex;
            return chain.proceed(args);
        });
    }

    private boolean readStoredSynologyMetadata(HookTargets targets)
            throws ReflectiveOperationException {
        Object galleryStats = targets.readGalleryStats.invoke(null, GalleryContract.DEVICE_ID);
        Integer storedPhotoCount = ColorOsGalleryBridge.photoCount(galleryStats);
        if (storedPhotoCount == null) {
            return false;
        }
        currentPhotoCount = storedPhotoCount;
        hasStoredSynologyMetadata = true;
        return true;
    }

    private void installLabelHook(HookTargets targets) {
        hook(targets.resolveFolderNote).intercept(chain -> {
            Object result = chain.proceed();
            if (result instanceof String value) {
                return HookPolicy.rewriteNasLabel(value);
            }
            return result;
        });
        hook(targets.resolveNasStateMessage).intercept(chain -> {
            Object result = chain.proceed();
            if (result instanceof String value) {
                return HookPolicy.rewriteNasStateMessage(value);
            }
            return result;
        });
    }

    private void installSyntheticDevicePresenceHook(HookTargets targets) {
        hook(targets.evaluateNasExitForRemovedDevice).intercept(chain -> {
            String deviceUserId = (String) chain.getArg(2);
            if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                return chain.proceed();
            }
            Object onResult = chain.getArg(3);
            targets.functionOneInvoke.invoke(onResult, Boolean.FALSE);
            logInfo("kept Synology DSM device active during NAS page resume");
            return null;
        });
    }

    private void installGalleryCardHooks(
            Context context,
            ApplicationInfo moduleApplicationInfo,
            HookTargets targets,
            GalleryRemoteClient remoteClient
    ) {
        hook(targets.bindNasAlbumsCard).intercept(chain -> {
            Object result = chain.proceed();
            try {
                ColorOsGalleryBridge.applySynologyCardBranding(
                        context,
                        moduleApplicationInfo,
                        chain.getThisObject(),
                        chain.getArg(2),
                        currentDeviceModel,
                        currentNasConnected
                );
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "Synology card branding failed", error);
            }
            return result;
        });
        hook(targets.applyNasAvailability).intercept(chain -> {
            Object result = chain.proceed();
            Integer state = (Integer) chain.getArg(0);
            boolean connected = state != null && state == 1;
            currentNasConnected = connected;
            try {
                ColorOsGalleryBridge.applySynologyConnectionLabel(
                        chain.getThisObject(),
                        remoteClient.currentDeviceModel(),
                        connected
                );
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "Synology connection label update failed", error);
            }
            return result;
        });
    }

    private void initializeNasState(GalleryRemoteClient remoteClient) {
        try {
            currentDeviceModel = remoteClient.configuredDeviceModel();
            currentNasConnected = remoteClient.isConfigured();
        } catch (IOException error) {
            log(Log.ERROR, TAG, "stored Synology device model lookup failed", error);
        }
    }

    private NasState currentNasState() {
        return new NasState(currentDeviceModel, currentNasConnected);
    }

    private NasState refreshNasState(GalleryRemoteClient remoteClient) {
        String model = currentDeviceModel;
        try {
            model = remoteClient.probeDeviceModel();
            currentDeviceModel = model;
            currentNasConnected = true;
            logInfo("Synology NAS connected: model=" + model);
        } catch (IOException error) {
            currentDeviceModel = model;
            currentNasConnected = false;
            log(Log.WARN, TAG, "Synology NAS connection probe failed", error);
        }
        return new NasState(currentDeviceModel, currentNasConnected);
    }

    private void refreshNasStateAsync(
            ClassLoader classLoader,
            GalleryRemoteClient remoteClient,
            Object nasStatusFlow
    ) {
        if (!nasStatusRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        nasStatusExecutor.execute(() -> {
            try {
                NasState nasState = refreshNasState(remoteClient);
                ColorOsGalleryBridge.updateStatusFlow(
                        nasStatusFlow,
                        classLoader,
                        nasState.connected()
                );
            } catch (ReflectiveOperationException | RuntimeException error) {
                log(Log.ERROR, TAG, "Synology NAS async status update failed", error);
            } finally {
                nasStatusRefreshInFlight.set(false);
            }
        });
    }

    private void logInfo(String message) {
        log(Log.INFO, TAG, message);
    }

    private static final class HookTargets {
        private final Method readBoolean;
        private final Method readBooleanDefault;
        private final Method openNasDeviceSpace;
        private final Method resolveFolderNote;
        private final Method resolveNasStateMessage;
        private final Constructor<?> cloudSyncProxyConstructor;
        private final Method listNasDevices;
        private final Method readGalleryStats;
        private final Method preloadNasMetadata;
        private final Method observeNasStatus;
        private final Method cancelNasDownload;
        private final Method populateMainTabAlbumGroups;
        private final Method bindNasAlbumsCard;
        private final Method applyNasAvailability;
        private final Method evaluateNasExitForRemovedDevice;
        private final Method functionOneInvoke;
        private final Method showMultiDeleteDialog;
        private final Field multiDeleteParams;
        private final Field deleteParamsDeviceId;
        private final Method showSingleDeleteDialog;
        private final Field singleDeleteItemPath;
        private final Method parseMediaPath;
        private final Method resolveMediaObject;
        private final Field nasMediaDeviceId;
        private final Method loadNasMediaMetadata;
        private final Method setDialogMessage;

        private HookTargets(
                Method readBoolean,
                Method readBooleanDefault,
                Method openNasDeviceSpace,
                Method resolveFolderNote,
                Method resolveNasStateMessage,
                Constructor<?> cloudSyncProxyConstructor,
                Method listNasDevices,
                Method readGalleryStats,
                Method preloadNasMetadata,
                Method observeNasStatus,
                Method cancelNasDownload,
                Method populateMainTabAlbumGroups,
                Method bindNasAlbumsCard,
                Method applyNasAvailability,
                Method evaluateNasExitForRemovedDevice,
                Method functionOneInvoke,
                Method showMultiDeleteDialog,
                Field multiDeleteParams,
                Field deleteParamsDeviceId,
                Method showSingleDeleteDialog,
                Field singleDeleteItemPath,
                Method parseMediaPath,
                Method resolveMediaObject,
                Field nasMediaDeviceId,
                Method loadNasMediaMetadata,
                Method setDialogMessage
        ) {
            this.readBoolean = readBoolean;
            this.readBooleanDefault = readBooleanDefault;
            this.openNasDeviceSpace = openNasDeviceSpace;
            this.resolveFolderNote = resolveFolderNote;
            this.resolveNasStateMessage = resolveNasStateMessage;
            this.cloudSyncProxyConstructor = cloudSyncProxyConstructor;
            this.listNasDevices = listNasDevices;
            this.readGalleryStats = readGalleryStats;
            this.preloadNasMetadata = preloadNasMetadata;
            this.observeNasStatus = observeNasStatus;
            this.cancelNasDownload = cancelNasDownload;
            this.populateMainTabAlbumGroups = populateMainTabAlbumGroups;
            this.bindNasAlbumsCard = bindNasAlbumsCard;
            this.applyNasAvailability = applyNasAvailability;
            this.evaluateNasExitForRemovedDevice = evaluateNasExitForRemovedDevice;
            this.functionOneInvoke = functionOneInvoke;
            this.showMultiDeleteDialog = showMultiDeleteDialog;
            this.multiDeleteParams = multiDeleteParams;
            this.deleteParamsDeviceId = deleteParamsDeviceId;
            this.showSingleDeleteDialog = showSingleDeleteDialog;
            this.singleDeleteItemPath = singleDeleteItemPath;
            this.parseMediaPath = parseMediaPath;
            this.resolveMediaObject = resolveMediaObject;
            this.nasMediaDeviceId = nasMediaDeviceId;
            this.loadNasMediaMetadata = loadNasMediaMetadata;
            this.setDialogMessage = setDialogMessage;
        }
    }

    private record NasState(String deviceModel, boolean connected) {
    }
}
