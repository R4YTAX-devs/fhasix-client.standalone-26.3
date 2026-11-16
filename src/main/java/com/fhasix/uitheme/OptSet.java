package com.fhasix.uitheme;

import com.fhasix.uitheme.mixin.SimpleOptionAccessor;
import net.minecraft.client.OptionInstance;

/** Sets an option past its slider range. Uses the accessor mixin, or plain reflection if that mixin did not apply. */
final class OptSet {
    private OptSet() {
    }

    @SuppressWarnings("unchecked")
    static void set(OptionInstance<?> opt, Object value) {
        try {
            ((SimpleOptionAccessor<Object>) (Object) opt).fhasix$setValue(value);
            return;
        } catch (Exception | LinkageError ignored) {
        }
        try {
            java.lang.reflect.Field f = OptionInstance.class.getDeclaredField("value");
            f.setAccessible(true);
            f.set(opt, value);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("options", t);
        }
    }
}
