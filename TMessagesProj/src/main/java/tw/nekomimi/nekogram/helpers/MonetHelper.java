package tw.nekomimi.nekogram.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PatternMatcher;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MonetHelper {
    private static final class MonetThemeSpec {
        final String assetName;
        final String templateAssetName;
        final boolean amoled;

        private MonetThemeSpec(String assetName, String templateAssetName, boolean amoled) {
            this.assetName = assetName;
            this.templateAssetName = templateAssetName;
            this.amoled = amoled;
        }
    }

    private static final HashMap<String, Integer> ids = new HashMap<>() {{
        put("a1_0", android.R.color.system_accent1_0);
        put("a1_10", android.R.color.system_accent1_10);
        put("a1_50", android.R.color.system_accent1_50);
        put("a1_100", android.R.color.system_accent1_100);
        put("a1_200", android.R.color.system_accent1_200);
        put("a1_300", android.R.color.system_accent1_300);
        put("a1_400", android.R.color.system_accent1_400);
        put("a1_500", android.R.color.system_accent1_500);
        put("a1_600", android.R.color.system_accent1_600);
        put("a1_700", android.R.color.system_accent1_700);
        put("a1_800", android.R.color.system_accent1_800);
        put("a1_900", android.R.color.system_accent1_900);
        put("a1_1000", android.R.color.system_accent1_1000);
        put("a2_0", android.R.color.system_accent2_0);
        put("a2_10", android.R.color.system_accent2_10);
        put("a2_50", android.R.color.system_accent2_50);
        put("a2_100", android.R.color.system_accent2_100);
        put("a2_200", android.R.color.system_accent2_200);
        put("a2_300", android.R.color.system_accent2_300);
        put("a2_400", android.R.color.system_accent2_400);
        put("a2_500", android.R.color.system_accent2_500);
        put("a2_600", android.R.color.system_accent2_600);
        put("a2_700", android.R.color.system_accent2_700);
        put("a2_800", android.R.color.system_accent2_800);
        put("a2_900", android.R.color.system_accent2_900);
        put("a2_1000", android.R.color.system_accent2_1000);
        put("a3_0", android.R.color.system_accent3_0);
        put("a3_10", android.R.color.system_accent3_10);
        put("a3_50", android.R.color.system_accent3_50);
        put("a3_100", android.R.color.system_accent3_100);
        put("a3_200", android.R.color.system_accent3_200);
        put("a3_300", android.R.color.system_accent3_300);
        put("a3_400", android.R.color.system_accent3_400);
        put("a3_500", android.R.color.system_accent3_500);
        put("a3_600", android.R.color.system_accent3_600);
        put("a3_700", android.R.color.system_accent3_700);
        put("a3_800", android.R.color.system_accent3_800);
        put("a3_900", android.R.color.system_accent3_900);
        put("a3_1000", android.R.color.system_accent3_1000);
        put("n1_0", android.R.color.system_neutral1_0);
        put("n1_10", android.R.color.system_neutral1_10);
        put("n1_50", android.R.color.system_neutral1_50);
        put("n1_100", android.R.color.system_neutral1_100);
        put("n1_200", android.R.color.system_neutral1_200);
        put("n1_300", android.R.color.system_neutral1_300);
        put("n1_400", android.R.color.system_neutral1_400);
        put("n1_500", android.R.color.system_neutral1_500);
        put("n1_600", android.R.color.system_neutral1_600);
        put("n1_700", android.R.color.system_neutral1_700);
        put("n1_800", android.R.color.system_neutral1_800);
        put("n1_900", android.R.color.system_neutral1_900);
        put("n1_1000", android.R.color.system_neutral1_1000);
        put("n2_0", android.R.color.system_neutral2_0);
        put("n2_10", android.R.color.system_neutral2_10);
        put("n2_50", android.R.color.system_neutral2_50);
        put("n2_100", android.R.color.system_neutral2_100);
        put("n2_200", android.R.color.system_neutral2_200);
        put("n2_300", android.R.color.system_neutral2_300);
        put("n2_400", android.R.color.system_neutral2_400);
        put("n2_500", android.R.color.system_neutral2_500);
        put("n2_600", android.R.color.system_neutral2_600);
        put("n2_700", android.R.color.system_neutral2_700);
        put("n2_800", android.R.color.system_neutral2_800);
        put("n2_900", android.R.color.system_neutral2_900);
        put("n2_1000", android.R.color.system_neutral2_1000);
        put("monetRedDark", R.color.monetRedDark);
        put("monetRedLight", R.color.monetRedLight);
        put("monetRedCall", R.color.monetRedCall);
        put("monetGreenCall", R.color.monetGreenCall);
    }};
    private static final HashMap<String, MonetThemeSpec> monetThemeSpecs = new HashMap<>() {{
        put("monet_light.attheme", new MonetThemeSpec("monet_light.attheme", "monet_light.attheme", false));
        put("monet_dark.attheme", new MonetThemeSpec("monet_dark.attheme", "monet_dark.attheme", false));
        put("monet_amoled.attheme", new MonetThemeSpec("monet_amoled.attheme", "monet_amoled.attheme", true));
    }};
    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final OverlayChangeReceiver overlayChangeReceiver = new OverlayChangeReceiver();
    private static final Object themeFileLock = new Object();

    public static int getColor(String color) {
        return getColor(color, false);
    }

    public static int getColor(String color, boolean amoled) {
        try {
            Integer resolvedColor = resolveColorToken(color, amoled);
            if (resolvedColor != null) {
                return resolvedColor;
            }
        } catch (Exception e) {
            FileLog.e("Error loading color " + color, e);
        }
        return 0;
    }

    public static File getThemeFile(String assetName) {
        MonetThemeSpec spec = monetThemeSpecs.get(assetName);
        if (spec == null || ApplicationLoader.applicationContext == null) {
            return null;
        }
        synchronized (themeFileLock) {
            try {
                String resolvedTheme = resolveThemeTemplate(spec);
                if (resolvedTheme == null) {
                    return null;
                }
                File outputFile = new File(ApplicationLoader.getFilesDirFixed(), spec.assetName);
                String currentTheme = readFile(outputFile);
                if (!resolvedTheme.equals(currentTheme)) {
                    writeFile(outputFile, resolvedTheme);
                }
                return outputFile;
            } catch (Exception e) {
                FileLog.e("Error generating monet theme " + assetName, e);
            }
        }
        return null;
    }

    private static String resolveThemeTemplate(MonetThemeSpec spec) throws IOException {
        try (InputStream stream = ApplicationLoader.applicationContext.getAssets().open(spec.templateAssetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(resolveTemplateLine(line, spec.amoled)).append('\n');
            }
            return builder.toString();
        }
    }

    private static String resolveTemplateLine(String line, boolean amoled) {
        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            return line;
        }

        String rawValue = line.substring(separatorIndex + 1).trim();
        if (rawValue.isEmpty() || rawValue.charAt(0) == '#' || Character.isDigit(rawValue.charAt(0)) || rawValue.charAt(0) == '-') {
            return line;
        }

        Integer resolvedColor = resolveColorToken(rawValue, amoled);
        if (resolvedColor == null) {
            return line;
        }
        return line.substring(0, separatorIndex + 1) + resolvedColor;
    }

    private static Integer resolveColorToken(String token, boolean amoled) {
        if (token == null) {
            return null;
        }
        String normalizedToken = token.trim();
        if (normalizedToken.isEmpty()) {
            return null;
        }

        int alphaStart = normalizedToken.indexOf('(');
        int alphaEnd = normalizedToken.indexOf(')');
        Integer alphaPercent = null;
        if (alphaStart > 0 && alphaEnd > alphaStart) {
            try {
                alphaPercent = Integer.parseInt(normalizedToken.substring(alphaStart + 1, alphaEnd));
            } catch (NumberFormatException ignore) {
                alphaPercent = null;
            }
        }

        String baseToken = alphaStart > 0 ? normalizedToken.substring(0, alphaStart) : normalizedToken;
        Integer baseColor;
        switch (baseToken) {
            case "mBlack":
                baseColor = 0xFF000000;
                break;
            case "mWhite":
                baseColor = 0xFFFFFFFF;
                break;
            case "mRed200":
                baseColor = 0xFFEF9A9A;
                break;
            case "mRed500":
                baseColor = 0xFFF44336;
                break;
            case "mGreen500":
                baseColor = 0xFF4CAF50;
                break;
            default:
                Integer id = ids.get(amoled && "n1_900".equals(baseToken) ? "n1_1000" : baseToken);
                if (id == null || ApplicationLoader.applicationContext == null) {
                    return null;
                }
                baseColor = ApplicationLoader.applicationContext.getColor(id);
                break;
        }

        if (alphaPercent == null) {
            return baseColor;
        }

        int clampedAlpha = Math.max(0, Math.min(alphaPercent, 100)) * 255 / 100;
        return (baseColor & 0x00FFFFFF) | (clampedAlpha << 24);
    }

    private static String readFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader streamReader = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static class OverlayChangeReceiver extends BroadcastReceiver {

        public void register(Context context) {
            IntentFilter packageFilter = new IntentFilter(ACTION_OVERLAY_CHANGED);
            packageFilter.addDataScheme("package");
            packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL);
            context.registerReceiver(this, packageFilter);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
                if (Theme.getActiveTheme().isMonet()) {
                    Theme.applyTheme(Theme.getActiveTheme());
                }
            }
        }
    }

    public static void registerReceiver(Context context) {
        overlayChangeReceiver.register(context);
    }

    public static void unregisterReceiver(Context context) {
        try {
            overlayChangeReceiver.unregister(context);
        } catch (IllegalArgumentException e) {
            FileLog.e(e);
        }
    }
}
