package cn.duoduo.bilisubtitle;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.text.SpannableString;
import android.widget.TextView;
import android.widget.Toast;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private String cachePath;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.bilibili.app.in")) return;

        Class<?> clazz = lpparam.classLoader.loadClass("com.alibaba.fastjson.parser.DefaultJSONParser");
        XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0 && param.args[0] instanceof String) {
                    if (((String) param.args[0]).contains("URLRequest")) {
                        log(((String) param.args[0]));
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {

            }
        });

        Class<?> clazz2 = lpparam.classLoader.loadClass("tv.danmaku.chronos.wrapper.chronosrpc.methods.receive.URLRequest$Response");
        XposedBridge.hookAllMethods(clazz2, "setContent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("URLRequest$Response " + param.args[0].toString());
                String content = param.args[0].toString(); // /download_files/chronos_file_7

                if (!Pattern.compile("/download_files/chronos_file_\\d+$").matcher(content).find())
                    return;

                try {
                    String subtitleFile = cachePath + content;
                    String subtitle = readFile(subtitleFile);
                    log("SubtitleJSON " + subtitle);
                    String subtitleSimple = ZhConverterUtil.toSimple(subtitle);
                    log("ConvertedJSON " + subtitleSimple);
                    writeFile(subtitleFile, subtitleSimple);
                    toast("字幕转换成功: " + subtitleFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("字幕转换失败: " + e.getMessage());
                }
            }
        });

        Class<?> clazz3 = lpparam.classLoader.loadClass("com.bilibili.common.chronoscommon.plugins.FileFormatParser");
        XposedBridge.hookAllConstructors(clazz3, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("FileFormatParser " + Arrays.toString(param.args));
                if (param.args.length == 4 && param.args[2] instanceof String) {
                    cachePath = (String) param.args[2];
                    // /data/user/0/com.bilibili.app.in/files/cron/20220724150946/sandbox
                }
            }
        });
    }

    private void log(String s) {
        XposedBridge.log("[BiliSubtitle] " + s);
    }

    private void toast(String s) {
        log("Toast: " + s);
        Application context = AndroidAppHelper.currentApplication();
        Toast.makeText(context, s, Toast.LENGTH_LONG).show();
    }

    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    static void writeFile(String path, String content) throws IOException {
        Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8));
    }
}
