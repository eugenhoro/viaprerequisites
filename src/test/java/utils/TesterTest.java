package utils;
import org.junit.Test;

import java.io.IOException;

public class TesterTest {
    @Test
    public void runNodeTest() throws IOException {
        new Tester().isNodeInstalled();
    }
}