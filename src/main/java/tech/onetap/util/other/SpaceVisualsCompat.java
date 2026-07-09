package tech.onetap.util.other;

public class SpaceVisualsCompat {
    private static Boolean loaded = null;

    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class.forName("galaxy.vis.Galaxy");
                loaded = true;
            } catch (ClassNotFoundException e) {
                loaded = false;
            }
        }
        return loaded;
    }
}
