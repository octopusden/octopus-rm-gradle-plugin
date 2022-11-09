package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContainerUtilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerUtilities.class);

    private ContainerUtilities() {
    }

    public static boolean isPodmanSupported() {
        if (System.getenv().getOrDefault("OS_TYPE", "NO").matches("(RHEL8.*)|(OL8.*)")) { //Backward compatibility
            return true;
        }
        try {
            final Process process = new ProcessBuilder().command("podman", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
