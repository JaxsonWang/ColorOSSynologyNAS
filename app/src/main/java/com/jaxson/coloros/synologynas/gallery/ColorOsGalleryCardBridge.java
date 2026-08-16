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

// 将群晖品牌、型号和连接状态写入 ColorOS NAS 卡片
final class ColorOsGalleryCardBridge {
    // 定位 ColorOS NAS 相册卡片当前使用的 ViewData 类型
    private static final String NAS_VIEW_DATA = "com.oplus.aiunit.vision.mjq";

    // 禁止实例化只负责 ColorOS 卡片视图适配的工具类
    private ColorOsGalleryCardBridge() {
    }

    // 为群晖卡片替换品牌 Logo，并同步型号和连接状态标签
    static void applyBranding(
            Context galleryContext, // ColorOS 相册进程的主题 Context
            ApplicationInfo moduleApplicationInfo, // 模块资源所在应用信息
            Object binding, // 当前版本 NasAlbumsViewDataBinding 实例
            Object viewData, // 当前卡片绑定的 mjq 数据
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        if (!isSynologyViewData(viewData)) {
            return;
        }

        // h9q.j 对应的 ColorOS NAS 卡片标题控件
        TextView title = (TextView) ColorOsGalleryReflection.readField(binding, "j");
        // 标题控件所在且同时承载 NAS Logo 的行容器
        Object parent = title.getParent();
        if (!(parent instanceof ViewGroup titleRow /* 已确认的标题行容器 */)) {
            throw new IllegalStateException("ColorOS NAS title row is not a ViewGroup");
        }
        // 当前相册资源中 iv_nas_title_icon 的实际 View ID
        int logoViewId = galleryContext.getResources().getIdentifier(
                "iv_nas_title_icon",
                "id",
                GalleryContract.GALLERY_PACKAGE
        );
        // 标题行中与固定资源 ID 对应的 NAS Logo View
        View logoView = titleRow.findViewById(logoViewId);
        if (!(logoView instanceof ImageView logo /* 已确认的 NAS Logo 控件 */)) {
            throw new IllegalStateException("ColorOS NAS title logo view is missing");
        }

        // 从相册进程读取模块 Logo 与背景所需的模块资源实例
        Resources moduleResources;
        try {
            moduleResources = galleryContext.getPackageManager().getResourcesForApplication(
                    moduleApplicationInfo
            );
        } catch (PackageManager.NameNotFoundException error /* 模块资源包解析异常 */) {
            throw new IllegalStateException("Synology module resources are missing", error);
        }
        // 模块内适配 ColorOS 卡片的群晖品牌 Logo
        Drawable logoDrawable = moduleResources.getDrawable(
                R.drawable.synology_logo,
                galleryContext.getTheme()
        );
        // 模块内适配 ColorOS 日间和夜间主题的 Logo 背景
        Drawable backgroundDrawable = moduleResources.getDrawable(
                R.drawable.synology_logo_background,
                galleryContext.getTheme()
        );
        // Logo 左右方向需要应用的 dp 转像素内边距
        int horizontalPadding = dp(galleryContext, 3);
        // Logo 上下方向需要应用的 dp 转像素内边距
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

        applyConnectionLabel(binding, deviceModel, connected);
    }

    // 仅对当前群晖卡片写入共享型号和连接状态标签
    static void applyConnectionLabel(
            Object binding, // 当前版本 NasAlbumsViewDataBinding 实例
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        if (!GalleryContract.DEVICE_ID.equals(
                ColorOsGalleryReflection.readField(binding, "T")
        )) {
            return;
        }
        // h9q.k 对应的 ColorOS NAS 卡片型号控件
        TextView model = (TextView) ColorOsGalleryReflection.readField(binding, "k");
        // h9q.l 对应的 ColorOS NAS 卡片连接状态控件
        TextView status = (TextView) ColorOsGalleryReflection.readField(binding, "l");
        // 型号控件当前布局参数，用于调整与 Logo 的固定间距
        ViewGroup.LayoutParams layoutParams = model.getLayoutParams();
        if (layoutParams instanceof
                // 已确认支持起始边距的布局参数
                ViewGroup.MarginLayoutParams marginLayoutParams) {
            marginLayoutParams.setMarginStart(dp(model.getContext(), 3));
            model.setLayoutParams(marginLayoutParams);
        }
        model.setText(deviceModel);
        status.setText(connected ? "已连接" : "未连接");
        status.setVisibility(View.VISIBLE);
    }

    // 判断当前 mjq ViewData 是否绑定唯一群晖设备标识
    private static boolean isSynologyViewData(Object viewData /* 当前卡片 ViewData */)
            throws ReflectiveOperationException {
        if (viewData == null || !NAS_VIEW_DATA.equals(viewData.getClass().getName())) {
            return false;
        }
        // mjq.b 对应的当前 NAS 设备信息 DTO
        Object deviceInfo = ColorOsGalleryReflection.readField(viewData, "b");
        return GalleryContract.DEVICE_ID.equals(
                ColorOsGalleryReflection.readField(deviceInfo, "b")
        );
    }

    // 将卡片间距的 dp 值按相册 Context 密度转换为像素
    private static int dp(
            Context context, // 提供当前显示密度的相册 Context
            int value // 需要转换的 dp 整数值
    ) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
