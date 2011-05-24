package hydna;

public class DebugHelper {
	public static void debugPrint(String c, int ch, String msg) {
		System.out.printf("HydnaDebug: %10s: %8x: %s\n", c, ch, msg);
	}
}
