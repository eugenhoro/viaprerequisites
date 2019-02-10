package utils;
public class Logger {
    public void debug(String s, String join, String rootDirectory) {
        System.out.println(s);
    }

    public void debug(String error) {
        debug(error, null, null);
    }

    public void error(String s, Object... args) {
        debug(s, null, null);
    }

    public void debug(String s, Object... args) {
        debug(s, null, null);
    }

    public void debug(String error, int exitStatus) {
        debug(error, null, null);
    }

    public void debug(String error, String exitStatus) {
        debug(error, null, null);
    }

    public void warn(String errorMessage, String message) {
        debug(errorMessage, null, null);
    }

    public void error(String s, StackTraceElement[] stackTrace) {
        debug(s, null, null);
    }
}
