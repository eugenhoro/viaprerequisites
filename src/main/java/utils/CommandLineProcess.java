package utils;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

public class CommandLineProcess {
    private static final long DEFAULT_TIMEOUT_READLINE_SECONDS = 300L;
    private static final long DEFAULT_TIMEOUT_PROCESS_MINUTES = 15L;
    private static final String WINDOWS_SEPARATOR = "\\";
    private final Logger logger = new Logger();
    private String rootDirectory;
    private String[] args;
    private long timeoutReadLineSeconds;
    private long timeoutProcessMinutes;
    private boolean errorInProcess = false;
    private Process processStart = null;
    private File errorLog = new File("error", ".log");

    public CommandLineProcess(String rootDirectory, String[] args) {
        this.rootDirectory = rootDirectory;
        this.args = args;
        this.timeoutReadLineSeconds = 300L;
        this.timeoutProcessMinutes = 15L;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public List<String> executeProcess() throws IOException {
        return this.executeProcess(true, false);
    }

    private List<String> executeProcess(boolean includeOutput, boolean includeErrorLines) throws IOException {
        List<String> linesOutput = new LinkedList();
        ProcessBuilder pb = new ProcessBuilder(this.args);
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            this.rootDirectory = this.getShortPath(this.rootDirectory);
        }
        pb.directory(new File(this.rootDirectory));
        String redirectErrorOutput = isWindows() ? "nul" : "/dev/null";
        if (includeErrorLines) {
            pb.redirectError(this.errorLog);
        } else {
            pb.redirectError(new File(redirectErrorOutput));
        }
        if (!includeOutput || includeErrorLines) {
            pb.redirectOutput(new File(redirectErrorOutput));
        }
        if (!includeErrorLines) {
            this.logger.debug("start execute command '{}' in '{}'", String.join(" ", this.args), this.rootDirectory);
        }
        this.processStart = pb.start();
        if (includeOutput) {
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            InputStreamReader inputStreamReader;
            if (!includeErrorLines) {
                inputStreamReader = new InputStreamReader(this.processStart.getInputStream());
            } else {
                inputStreamReader = new InputStreamReader(this.processStart.getErrorStream());
            }
            BufferedReader reader = new BufferedReader(inputStreamReader);
            this.errorInProcess = this.readBlock(inputStreamReader, reader, executorService, linesOutput, includeErrorLines);
        }
        try {
            this.processStart.waitFor(this.timeoutProcessMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException var10) {
            this.errorInProcess = true;
            this.logger.error("'{}' was interrupted {}", this.args, var10);
        }
        if (this.processStart.isAlive() && this.errorInProcess) {
            this.logger.debug("error executing command destroying process");
            this.processStart.destroy();
            return linesOutput;
        } else {
            if (this.getExitStatus() != 0) {
                this.logger.debug("error in execute command {}", this.getExitStatus());
                this.errorInProcess = true;
            }
            this.printErrors();
            return linesOutput;
        }
    }

    private void printErrors() {
        if (this.errorLog.isFile()) {
            try {
                FileReader fileReader = new FileReader(this.errorLog);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String currLine;
                while ((currLine = bufferedReader.readLine()) != null) {
                    this.logger.debug(currLine);
                }
                fileReader.close();
            } catch (Exception var12) {
                this.logger.warn("Error printing cmd command errors {} ", var12.getMessage());
                this.logger.debug("Error: {}", var12.getStackTrace());
            } finally {
                try {
                    FileUtils.forceDelete(this.errorLog);
                } catch (IOException var11) {
                    this.logger.warn("Error closing cmd command errors file {} ", var11.getMessage());
                    this.logger.debug("Error: {}", var11.getStackTrace());
                }
            }
        }
    }

    private String getShortPath(String rootPath) {
        File file = new File(rootPath);
        String lastPathAfterSeparator = null;
        String shortPath = this.getWindowsShortPath(file.getAbsolutePath());
        if (StringUtils.isNotEmpty(shortPath)) {
            return this.getWindowsShortPath(file.getAbsolutePath());
        } else {
            for (; StringUtils.isEmpty(this.getWindowsShortPath(file.getAbsolutePath())); file = file.getParentFile()) {
                String filePath = file.getAbsolutePath();
                if (StringUtils.isNotEmpty(lastPathAfterSeparator)) {
                    lastPathAfterSeparator = file.getAbsolutePath().substring(filePath.lastIndexOf("\\"), filePath.length()) + lastPathAfterSeparator;
                } else {
                    lastPathAfterSeparator = file.getAbsolutePath().substring(filePath.lastIndexOf("\\"), filePath.length());
                }
            }
            return this.getWindowsShortPath(file.getAbsolutePath()) + lastPathAfterSeparator;
        }
    }

    private String getWindowsShortPath(String path) {
        if (path.length() >= 256) {
            char[] result = new char[256];
            Kernel32.INSTANCE.GetShortPathName(path, result, result.length);
            return Native.toString(result);
        } else {
            return path;
        }
    }

    private boolean readBlock(InputStreamReader inputStreamReader, BufferedReader reader, ExecutorService executorService, List<String> lines, boolean includeErrorLines) {
        boolean wasError = false;
        boolean continueReadingLines = true;
        try {
            if (!includeErrorLines) {
                this.logger.debug("trying to read lines using '{}'", this.commandArgsToString());
            }
            int lineIndex = 1;
            for (String line = ""; continueReadingLines && line != null; ++lineIndex) {
                Future future = executorService.submit(new CommandLineProcess.ReadLineTask(reader));
                try {
                    line = (String) future.get(this.timeoutReadLineSeconds, TimeUnit.SECONDS);
                    if (!includeErrorLines) {
                        if (StringUtils.isNotBlank(line)) {
                            this.logger.debug("Read line #{}: {}", lineIndex, line);
                            lines.add(line);
                        } else {
                            this.logger.debug("Finished reading {} lines", lineIndex - 1);
                        }
                    } else if (StringUtils.isNotBlank(line)) {
                        lines.add(line);
                    }
                } catch (TimeoutException var17) {
                    this.logger.debug("Received timeout when reading line #" + lineIndex, var17.getStackTrace());
                    continueReadingLines = false;
                    wasError = true;
                } catch (Exception var18) {
                    this.logger.debug("Error reading line #" + lineIndex, var18.getStackTrace());
                    continueReadingLines = false;
                    wasError = true;
                }
            }
        } catch (Exception var19) {
            this.logger.error("error parsing output : {}", var19.getStackTrace());
        } finally {
            executorService.shutdown();
            IOUtils.closeQuietly(inputStreamReader);
            IOUtils.closeQuietly(reader);
        }
        return wasError;
    }

    private String commandArgsToString() {
        StringBuilder result = new StringBuilder("");
        String[] var2 = this.args;
        int var3 = var2.length;
        for (int var4 = 0; var4 < var3; ++var4) {
            String arg = var2[var4];
            result.append(arg + " ");
        }
        result.deleteCharAt(result.length() - 1);
        return result.toString();
    }

    public void executeProcessWithoutOutput() throws IOException {
        this.executeProcess(false, false);
    }

    public List<String> executeProcessWithErrorOutput() throws IOException {
        return this.executeProcess(false, true);
    }

    public void setTimeoutReadLineSeconds(long timeoutReadLineSeconds) {
        this.timeoutReadLineSeconds = timeoutReadLineSeconds;
    }

    public void setTimeoutProcessMinutes(long timeoutProcessMinutes) {
        this.timeoutProcessMinutes = timeoutProcessMinutes;
    }

    public boolean isErrorInProcess() {
        return this.errorInProcess;
    }

    public int getExitStatus() {
        return this.processStart != null ? this.processStart.exitValue() : 0;
    }

    class ReadLineTask implements Callable<String> {
        private final BufferedReader reader;

        ReadLineTask(BufferedReader reader) {
            this.reader = reader;
        }

        public String call() throws Exception {
            return this.reader.readLine();
        }
    }
}

