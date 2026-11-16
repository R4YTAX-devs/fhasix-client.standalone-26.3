package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Camera mod: freelook rotation + third-person distance / shoulder / height offsets.
 * Each wrap changes one call inside Camera.update(...). require = 0 so a renamed call in a future
 * version just disables that part instead of crashing the game.
 */
@Mixin(Camera.class)
public abstract class NewCameraMixin {

    /** Freelook: while active the camera uses its own yaw/pitch instead of the player's. */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
            require = 0)
    private void fhasix$rotationCart(Camera self, float yaw, float pitch, Operation<Void> original) {
        fhasix$apply(self, yaw, pitch, false, original);
    }

    /** Ordinary first / third person (behind) view. */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1),
            require = 0)
    private void fhasix$rotation(Camera self, float yaw, float pitch, Operation<Void> original) {
        fhasix$apply(self, yaw, pitch, false, original);
    }

    /** Third person (front) view looks back at the player, so yaw is turned 180 and pitch flipped. */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 2),
            require = 0)
    private void fhasix$rotationFront(Camera self, float yaw, float pitch, Operation<Void> original) {
        fhasix$apply(self, yaw, pitch, true, original);
    }

    private static void fhasix$apply(Camera self, float yaw, float pitch, boolean mirrored, Operation<Void> original) {
        try {
            if (NewCamera.freelookActive()) {
                yaw = mirrored ? NewCamera.yaw() + 180f : NewCamera.yaw();
                pitch = mirrored ? -NewCamera.pitch() : NewCamera.pitch();
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        original.call(self, yaw, pitch);
    }

    /** Third-person distance. */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
            require = 0)
    private float fhasix$distance(Camera self, float distance, Operation<Float> original) {
        float scale = 1f;
        try {
            scale = NewCamera.distanceScale();
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        return original.call(self, distance * scale);
    }

    /** Shoulder + height offset (only for the third-person "move backwards" call, whose first argument is negative). */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V"),
            require = 0)
    private void fhasix$offset(Camera self, float x, float y, float z, Operation<Void> original) {
        try {
            if (x < 0f) {
                y += NewCamera.height();
                z -= NewCamera.shoulder(); // third argument moves toward the camera's LEFT, so subtract for "right"
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        original.call(self, x, y, z);
    }
}
