package com.unlock.islandwhitelist;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 解除 HyperOS 超级岛白名单限制。
 * 逻辑与 XiaomiHelper 的 IslandWhitelist 相同，但没有任何系统版本检查，
 * 也没有设置界面：在 LSPosed 里启用并勾选「系统界面」即生效。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[UnlockIsland] ";
    private static final String TARGET_PKG = "com.android.systemui";

    private static final String CLS_SIGNATURE_CHECKER =
            "miui.systemui.notification.focus.SignatureChecker";
    private static final String CLS_SETTINGS_MANAGER =
            "miui.systemui.notification.NotificationSettingsManager";

    // 已经处理过的类，避免同一个类被重复 hook
    private static final Set<Class<?>> HOOKED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>()));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;
        log("loaded in " + lpparam.packageName + ", SDK=" + android.os.Build.VERSION.SDK_INT);

        // 1) 先尝试 SystemUI 自己的 ClassLoader
        tryHook(lpparam.classLoader);

        // 2) 目标类位于 SystemUI 插件（独立 APK）里，插件 ClassLoader 是之后才创建的，
        //    所以监听 ClassLoader 的创建，在创建完成后立刻尝试 hook。
        XC_MethodHook loaderCreated = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof ClassLoader) {
                    tryHook((ClassLoader) param.thisObject);
                }
            }
        };
        try {
            XposedBridge.hookAllConstructors(PathClassLoader.class, loaderCreated);
            XposedBridge.hookAllConstructors(DexClassLoader.class, loaderCreated);
        } catch (Throwable t) {
            log("hook ClassLoader constructors failed: " + t);
        }
    }

    private static void tryHook(ClassLoader cl) {
        if (cl == null) return;
        hookSignatureChecker(cl);
        hookSettingsManager(cl);
    }

    private static void hookSignatureChecker(ClassLoader cl) {
        Class<?> c = findClass(CLS_SIGNATURE_CHECKER, cl);
        if (c == null || !HOOKED.add(c)) return;
        hookReturnTrue(c, "checkSignatures");
    }

    private static void hookSettingsManager(ClassLoader cl) {
        Class<?> c = findClass(CLS_SETTINGS_MANAGER, cl);
        if (c == null || !HOOKED.add(c)) return;
        hookReturnTrue(c, "canShowFocus");
        hookReturnTrue(c, "canCustomFocus");
        hookReturnTrue(c, "mediaIslandSupportMiniWindow");
    }

    private static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 把类里所有同名且返回 boolean 的方法替换为恒返回 true。 */
    private static void hookReturnTrue(Class<?> c, String methodName) {
        int hooked = 0;
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != Boolean.class) {
                    log(c.getSimpleName() + "." + methodName + " skipped, return type " + rt);
                    continue;
                }
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true));
                hooked++;
            }
        } catch (Throwable t) {
            log("hook " + c.getSimpleName() + "." + methodName + " failed: " + t);
            return;
        }
        if (hooked > 0) {
            log("hooked " + c.getSimpleName() + "." + methodName + " x" + hooked);
        } else {
            log("method not found: " + c.getName() + "." + methodName
                    + " | declared: " + describeMethods(c));
        }
    }

    // 方法没找到时（比如新系统改名），把类里的方法名打到日志里方便排查
    private static String describeMethods(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (sb.length() > 600) {
                    sb.append("...");
                    break;
                }
                sb.append(m.getName()).append(',');
            }
        } catch (Throwable t) {
            sb.append("<error ").append(t).append('>');
        }
        return sb.toString();
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
