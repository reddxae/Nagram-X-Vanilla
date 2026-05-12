package org.telegram.messenger.video;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.IOException;

public class RoundVideoFileUtils {
    public static boolean deleteIfExists(File file, String errorMessage) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.delete()) {
            return true;
        }
        if (errorMessage != null) {
            FileLog.e(errorMessage + " " + file);
        }
        return false;
    }

    public static boolean replaceFile(File sourceFile, File targetFile, String logPrefix) {
        if (sourceFile == null || targetFile == null) {
            FileLog.e(logPrefix + " source or target file is null");
            return false;
        }
        if (sourceFile.equals(targetFile)) {
            return targetFile.exists() && targetFile.length() > 0;
        }
        deleteIfExists(targetFile, logPrefix + " unable to delete target before replace");
        if (sourceFile.renameTo(targetFile)) {
            return targetFile.exists() && targetFile.length() > 0;
        }
        FileLog.e(logPrefix + " unable to rename file, falling back to copy");
        try {
            if (!AndroidUtilities.copyFile(sourceFile, targetFile)) {
                FileLog.e(logPrefix + " unable to copy file");
                return false;
            }
        } catch (IOException e) {
            FileLog.e(e);
            FileLog.e(logPrefix + " unable to copy file");
            return false;
        }
        deleteIfExists(sourceFile, logPrefix + " unable to delete source after copy");
        return targetFile.exists() && targetFile.length() > 0;
    }

    private RoundVideoFileUtils() {
    }
}
