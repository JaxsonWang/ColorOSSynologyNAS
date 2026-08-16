package com.jaxson.coloros.synologynas.gallery;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.jaxson.coloros.synologynas.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.IntSupplier;

public final class ColorOsGalleryBridge {
    private static final String NAS_IMPL = "com.oplus.aiunit.vision.alq";
    private static final String NAS_REGISTRY = "com.oplus.aiunit.vision.xhb";
    private static final String NAS_DEVICE = "com.oplus.aiunit.vision.ngq";
    private static final String GALLERY_STATS_DTO = "com.oplus.aiunit.vision.jjq";
    private static final String NAS_HOME_GROUP = "com.oplus.aiunit.vision.e5q";
    private static final String NAS_DEVICE_INFO = "com.oplus.aiunit.vision.ogq";
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    private static final String DEVICE_STATUS = "com.oplus.aiunit.vision.srb";
    private static final String DEVICE_AVAILABILITY =
            "com.oplus.gallery.business_lib.nas.NasDeviceAvailability";
    private static final String NAS_VIEW_DATA = "com.oplus.aiunit.vision.mjq";
    private ColorOsGalleryBridge() {
    }

    public static void replaceProvider(
            Object cloudSyncProxy,
            ClassLoader classLoader,
            GalleryRemoteClient client,
            GalleryBackupService backupService,
            IntSupplier photoCount
    ) throws ReflectiveOperationException {
        Object nas = readFieldByType(cloudSyncProxy, NAS_IMPL);
        Object registry = readFieldByType(nas, NAS_REGISTRY);
        Map<Object, Object> providers = registryMap(registry);
        Object feiniu = enumValue(classLoader, NAS_PROVIDER, "FEINIU");
        Object original = providers.get(feiniu);
        if (original == null || ColorOsNasProviderProxy.isSynologyProvider(original)) {
            return;
        }
        providers.put(
                feiniu,
                ColorOsNasProviderProxy.create(
                        client,
                        backupService,
                        original,
                        classLoader,
                        photoCount
                )
        );
    }

    public static ArrayList<Object> withSynologyDevice(
            ArrayList<?> original,
            ClassLoader classLoader,
            String deviceModel,
            boolean connected
    ) throws ReflectiveOperationException {
        ArrayList<Object> result = new ArrayList<>(original.size() + 1);
        for (Object device : original) {
            if (!isSyntheticDevice(device)) {
                result.add(device);
            }
        }
        result.add(newDevice(classLoader, deviceModel, connected));
        return result;
    }

    public static Object connectedFlow(ClassLoader classLoader, boolean configured)
            throws ReflectiveOperationException {
        Object status = newStatusInfo(classLoader, configured);
        Class<?> flowKt = Class.forName(
                "kotlinx.coroutines.flow.FlowKt",
                false,
                classLoader
        );
        Method flowOf = flowKt.getMethod("flowOf", Object.class);
        return flowOf.invoke(null, status);
    }

    public static Object mutableStatusFlow(ClassLoader classLoader, boolean connected)
            throws ReflectiveOperationException {
        Object status = newStatusInfo(classLoader, connected);
        Class<?> stateFlowKt = Class.forName(
                "kotlinx.coroutines.flow.StateFlowKt",
                false,
                classLoader
        );
        Method mutableStateFlow = stateFlowKt.getMethod("MutableStateFlow", Object.class);
        return mutableStateFlow.invoke(null, status);
    }

    public static void updateStatusFlow(
            Object statusFlow,
            ClassLoader classLoader,
            boolean connected
    ) throws ReflectiveOperationException {
        Class<?> mutableStateFlow = Class.forName(
                "kotlinx.coroutines.flow.MutableStateFlow",
                false,
                classLoader
        );
        Method setValue = mutableStateFlow.getMethod("setValue", Object.class);
        setValue.invoke(statusFlow, newStatusInfo(classLoader, connected));
    }

    private static Object newStatusInfo(ClassLoader classLoader, boolean connected)
            throws ReflectiveOperationException {
        Object availability = enumValue(
                classLoader,
                DEVICE_AVAILABILITY,
                connected ? "CONNECTED" : "OFFLINE"
        );
        Class<?> statusType = Class.forName(DEVICE_STATUS, false, classLoader);
        Constructor<?> statusConstructor = statusType.getDeclaredConstructor(
                String.class,
                availability.getClass()
        );
        statusConstructor.setAccessible(true);
        return statusConstructor.newInstance(GalleryContract.DEVICE_ID, availability);
    }

    public static Object galleryStats(ClassLoader classLoader, int photoCount)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(GALLERY_STATS_DTO, false, classLoader);
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(photoCount, 0);
    }

    public static Integer photoCount(Object galleryStats) throws ReflectiveOperationException {
        if (galleryStats == null) {
            return null;
        }
        for (String fieldName : new String[]{"photoCount", "a", "f13529a"}) {
            try {
                Field field = galleryStats.getClass().getDeclaredField(fieldName);
                if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field.getInt(galleryStats);
                }
            } catch (NoSuchFieldException ignored) {
                // Try the next known runtime/fixture field name.
            }
        }
        for (Field field : galleryStats.getClass().getDeclaredFields()) {
            if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field.getInt(galleryStats);
            }
        }
        throw new NoSuchFieldException("ColorOS NAS gallery photo count");
    }

    public static int nextPreloadIndex(
            ArrayList<?> devices,
            int currentIndex,
            boolean hasStoredSynologyMetadata
    ) throws IllegalAccessException {
        if (!hasStoredSynologyMetadata) {
            return currentIndex;
        }
        int nextIndex = currentIndex;
        while (nextIndex < devices.size() && isSyntheticDevice(devices.get(nextIndex))) {
            nextIndex++;
        }
        return nextIndex;
    }

    public static boolean ensureSynologyHomeEntry(
            Object mainTabAlbumSetModel,
            ClassLoader classLoader,
            String deviceModel,
            boolean connected
    ) throws ReflectiveOperationException {
        ArrayList<Object> items = mainTabItems(mainTabAlbumSetModel);
        Object entry = newHomeEntry(classLoader, deviceModel, connected);

        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            if (!isSynologyHomeEntry(item)) {
                continue;
            }
            if (isCompleteSynologyHomeEntry(item, deviceModel, connected)) {
                return false;
            }
            items.set(index, entry);
            return true;
        }

        int insertionIndex = items.size();
        for (int index = 0; index < items.size(); index++) {
            if (readIntField(items.get(index), "a") == 4) {
                insertionIndex = index;
                break;
            }
        }
        items.add(insertionIndex, entry);
        return true;
    }

    public static boolean isConfiguredProvider(Object provider) {
        if (!ColorOsNasProviderProxy.isSynologyProvider(provider)) {
            return false;
        }
        return ((ColorOsNasProviderProxy) java.lang.reflect.Proxy.getInvocationHandler(provider))
                .isConfigured();
    }

    public static boolean isConfiguredManager(Object manager)
            throws ReflectiveOperationException {
        Object registry = readFieldByType(manager, NAS_REGISTRY);
        Map<Object, Object> providers = registryMap(registry);
        Object feiniu = enumValue(manager.getClass().getClassLoader(), NAS_PROVIDER, "FEINIU");
        return isConfiguredProvider(providers.get(feiniu));
    }

    public static void applySynologyCardBranding(
            Context galleryContext,
            ApplicationInfo moduleApplicationInfo,
            Object binding,
            Object viewData,
            String deviceModel,
            boolean connected
    ) throws ReflectiveOperationException {
        if (!isSynologyViewData(viewData)) {
            return;
        }

        TextView title = (TextView) readField(binding, "j");
        Object parent = title.getParent();
        if (!(parent instanceof ViewGroup titleRow)) {
            throw new IllegalStateException("ColorOS NAS title row is not a ViewGroup");
        }
        int logoViewId = galleryContext.getResources().getIdentifier(
                "iv_nas_title_icon",
                "id",
                GalleryContract.GALLERY_PACKAGE
        );
        View logoView = titleRow.findViewById(logoViewId);
        if (!(logoView instanceof ImageView logo)) {
            throw new IllegalStateException("ColorOS NAS title logo view is missing");
        }

        Resources moduleResources;
        try {
            moduleResources = galleryContext.getPackageManager().getResourcesForApplication(
                    moduleApplicationInfo
            );
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Synology module resources are missing", error);
        }
        Drawable logoDrawable = moduleResources.getDrawable(
                R.drawable.synology_logo,
                galleryContext.getTheme()
        );
        Drawable backgroundDrawable = moduleResources.getDrawable(
                R.drawable.synology_logo_background,
                galleryContext.getTheme()
        );
        int horizontalPadding = dp(galleryContext, 3);
        int verticalPadding = dp(galleryContext, 1);
        logo.setAdjustViewBounds(true);
        logo.setImageDrawable(logoDrawable);
        logo.setBackground(backgroundDrawable);
        logo.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        applySynologyConnectionLabel(binding, deviceModel, connected);
    }

    public static void applySynologyConnectionLabel(
            Object binding,
            String deviceModel,
            boolean connected
    ) throws ReflectiveOperationException {
        if (!GalleryContract.DEVICE_ID.equals(readField(binding, "T"))) {
            return;
        }
        TextView model = (TextView) readField(binding, "k");
        TextView status = (TextView) readField(binding, "l");
        ViewGroup.LayoutParams layoutParams = model.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams marginLayoutParams) {
            marginLayoutParams.setMarginStart(dp(model.getContext(), 3));
            model.setLayoutParams(marginLayoutParams);
        }
        model.setText(deviceModel);
        status.setText(connected ? "已连接" : "未连接");
        status.setVisibility(View.VISIBLE);
    }

    private static Object newDevice(
            ClassLoader classLoader,
            String deviceModel,
            boolean connected
    )
            throws ReflectiveOperationException {
        Object provider = enumValue(classLoader, NAS_PROVIDER, "FEINIU");
        Class<?> type = Class.forName(NAS_DEVICE, false, classLoader);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                provider,
                GalleryContract.DEVICE_ID,
                GalleryContract.DEVICE_NAME,
                deviceModel,
                "synology://dsm7",
                "",
                connected ? 1 : 0,
                "",
                Long.MAX_VALUE,
                true,
                0
        );
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<Object> mainTabItems(Object model)
            throws ReflectiveOperationException {
        Field itemsLazyField = model.getClass().getDeclaredField("e");
        itemsLazyField.setAccessible(true);
        Object itemsLazy = itemsLazyField.get(model);
        Method getValue = itemsLazy.getClass().getDeclaredMethod("getValue");
        getValue.setAccessible(true);
        Object value = getValue.invoke(itemsLazy);
        if (!(value instanceof ArrayList<?>)) {
            throw new IllegalStateException("ColorOS main album groups are not an ArrayList");
        }
        return (ArrayList<Object>) value;
    }

    private static Object newHomeEntry(
            ClassLoader classLoader,
            String deviceModel,
            boolean connected
    )
            throws ReflectiveOperationException {
        Class<?> deviceInfoType = Class.forName(NAS_DEVICE_INFO, false, classLoader);
        Constructor<?> deviceInfoConstructor = deviceInfoType.getDeclaredConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                long.class,
                String.class,
                String.class,
                String.class
        );
        deviceInfoConstructor.setAccessible(true);
        Object deviceInfo = deviceInfoConstructor.newInstance(
                0,
                connected ? 1 : 0,
                1,
                1,
                0L,
                GalleryContract.DEVICE_ID,
                GalleryContract.DEVICE_NAME,
                deviceModel
        );

        Class<?> homeGroupType = Class.forName(NAS_HOME_GROUP, false, classLoader);
        Constructor<?> homeGroupConstructor = homeGroupType.getDeclaredConstructor(
                int.class,
                int.class,
                deviceInfoType
        );
        homeGroupConstructor.setAccessible(true);
        return homeGroupConstructor.newInstance(5, 1, deviceInfo);
    }

    private static boolean isSynologyHomeEntry(Object item)
            throws ReflectiveOperationException {
        if (item == null || !NAS_HOME_GROUP.equals(item.getClass().getName())) {
            return false;
        }
        Object deviceInfo = readField(item, "d");
        return GalleryContract.DEVICE_ID.equals(readField(deviceInfo, "b"));
    }

    private static boolean isCompleteSynologyHomeEntry(
            Object item,
            String deviceModel,
            boolean connected
    )
            throws ReflectiveOperationException {
        Object deviceInfo = readField(item, "d");
        return GalleryContract.DEVICE_NAME.equals(readField(deviceInfo, "c"))
                && readIntField(deviceInfo, "d") == (connected ? 1 : 0)
                && deviceModel.equals(readField(deviceInfo, "f"));
    }

    private static boolean isSynologyViewData(Object viewData)
            throws ReflectiveOperationException {
        if (viewData == null || !NAS_VIEW_DATA.equals(viewData.getClass().getName())) {
            return false;
        }
        Object deviceInfo = readField(viewData, "b");
        return GalleryContract.DEVICE_ID.equals(readField(deviceInfo, "b"));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Object readField(Object owner, String name)
            throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static int readIntField(Object owner, String name)
            throws ReflectiveOperationException {
        Object value = readField(owner, name);
        if (!(value instanceof Integer integer)) {
            throw new IllegalStateException(
                    owner.getClass().getName() + "." + name + " is not an int"
            );
        }
        return integer;
    }

    private static Object readFieldByType(Object owner, String className)
            throws ReflectiveOperationException {
        for (Field field : owner.getClass().getDeclaredFields()) {
            if (field.getType().getName().equals(className)) {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) {
                    return value;
                }
            }
        }
        throw new NoSuchFieldException(
                owner.getClass().getName() + " field of type " + className
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> registryMap(Object registry)
            throws ReflectiveOperationException {
        for (Field field : registry.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (Map<Object, Object>) field.get(registry);
            }
        }
        throw new NoSuchFieldException("ColorOS NAS registry map");
    }

    private static boolean isSyntheticDevice(Object owner)
            throws IllegalAccessException {
        for (Field field : owner.getClass().getDeclaredFields()) {
            if (field.getType() == String.class) {
                field.setAccessible(true);
                if (GalleryContract.DEVICE_ID.equals(field.get(owner))) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(ClassLoader classLoader, String className, String value)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, false, classLoader);
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }
}
