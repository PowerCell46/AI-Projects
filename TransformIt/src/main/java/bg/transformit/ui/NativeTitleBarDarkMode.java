package bg.transformit.ui;


import com.sun.glass.ui.Window;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;


/**
 * Applies Windows 11 dark-mode chrome to the native title bar by calling
 * {@code DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE)} via the FFM API.
 * All failures are silently swallowed — title-bar colour is purely cosmetic.
 */
public final class NativeTitleBarDarkMode {

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;


    private NativeTitleBarDarkMode() {}


    public static void apply() {
        if (!isWindows()) return;

        try {
            long hwnd = resolveHwnd();
            if (hwnd == 0) return;
            applyDarkAttribute(hwnd);
        } catch (Throwable ignored) {
            // Never crash the application over a cosmetic detail.
        }
    }


    private static long resolveHwnd() {
        List<Window> windows = Window.getWindows();
        if (windows.isEmpty()) return 0L;
        return windows.get(0).getNativeHandle();
    }


    private static void applyDarkAttribute(long hwnd) throws Throwable {
        SymbolLookup dwmapi = SymbolLookup.libraryLookup("dwmapi", Arena.global());

        MethodHandle dwmSetWindowAttribute = Linker.nativeLinker()
                .downcallHandle(
                        dwmapi.find("DwmSetWindowAttribute").orElseThrow(),
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,   // HRESULT return value
                                ValueLayout.ADDRESS,    // HWND hwnd
                                ValueLayout.JAVA_INT,   // DWORD dwAttribute
                                ValueLayout.ADDRESS,    // LPCVOID pvAttribute
                                ValueLayout.JAVA_INT    // DWORD cbAttribute
                        )
                );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hwndSeg  = MemorySegment.ofAddress(hwnd);
            MemorySegment valueSeg = arena.allocate(ValueLayout.JAVA_INT);
            valueSeg.set(ValueLayout.JAVA_INT, 0, 1);   // TRUE

            dwmSetWindowAttribute.invoke(hwndSeg, DWMWA_USE_IMMERSIVE_DARK_MODE, valueSeg, 4);
        }
    }


    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }
}
