package utils;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class Tester {
    private static final String NODE = "node" ;
    private final Logger logger = new Logger();

    public void runNode() throws IOException {
        String appdir = System.getProperty("user.dir");
        CommandLineProcess mvnDependencies = new CommandLineProcess(Paths.get(appdir).toAbsolutePath().toString(),
                getMavenArgs());
        List<String> strings = mvnDependencies.executeProcess();
        System.out.println(String.join(System.lineSeparator(), strings));
    }

    private String[] getMavenArgs() {
        return new String[]{"node -v"};
    }

    public boolean isNodeInstalled(){
        boolean installed;
        try {
            CommandLineProcess nodeProcess = new CommandLineProcess(CommandLineProcess.isWindows() ? Constants.C : Constants.FORWARD_SLASH, new String[]{NODE, Constants.VERSION_PARAMETER});
            List<String> lines = nodeProcess.executeProcess();
            if (nodeProcess.isErrorInProcess() || lines.isEmpty()) {
                logger.debug("Node not installed");
                installed = false;
            } else {
                logger.debug("Node installed : {}", lines);
                installed = true;
            }
        } catch (IOException io) {
            logger.debug("Node not installed : {}", io.getMessage());
            installed = false;
        }

        return installed;
    }
}
