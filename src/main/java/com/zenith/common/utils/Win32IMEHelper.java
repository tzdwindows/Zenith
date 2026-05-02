package com.zenith.common.utils;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import java.util.Arrays;
import java.util.List;

public class Win32IMEHelper {
    public interface Imm32 extends StdCallLibrary {
        Imm32 INSTANCE = Native.load("imm32", Imm32.class);
        Pointer ImmGetContext(Pointer hwnd);
        boolean ImmSetCompositionWindow(Pointer himc, COMPOSITIONFORM lpCompForm);
        boolean ImmReleaseContext(Pointer hwnd, Pointer himc);
    }

    @Structure.FieldOrder({"dwStyle", "ptCurrentPos", "rcArea"})
    public static class COMPOSITIONFORM extends Structure {
        public int dwStyle;
        public POINT ptCurrentPos;
        public RECT rcArea;
    }

    @Structure.FieldOrder({"x", "y"})
    public static class POINT extends Structure { public int x, y; }

    @Structure.FieldOrder({"left", "top", "right", "bottom"})
    public static class RECT extends Structure { public int left, top, right, bottom; }

    private static final int CFS_POINT = 0x0002;

    public static void setIMEPosition(long hwndPtr, int x, int y) {
        if (hwndPtr == 0) return;
        Pointer hwnd = new Pointer(hwndPtr);
        Pointer himc = Imm32.INSTANCE.ImmGetContext(hwnd);
        if (himc != null) {
            COMPOSITIONFORM form = new COMPOSITIONFORM();
            form.dwStyle = CFS_POINT;
            form.ptCurrentPos = new POINT();
            form.ptCurrentPos.x = x;
            form.ptCurrentPos.y = y;
            Imm32.INSTANCE.ImmSetCompositionWindow(himc, form);
            Imm32.INSTANCE.ImmReleaseContext(hwnd, himc);
        }
    }
}