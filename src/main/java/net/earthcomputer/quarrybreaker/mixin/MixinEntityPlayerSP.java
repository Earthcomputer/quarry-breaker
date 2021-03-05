package net.earthcomputer.quarrybreaker.mixin;

import net.earthcomputer.quarrybreaker.LiteModQuarryBreaker;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (message.startsWith("/quarrybreaker")) {
            String[] rawArgs = message.split(" ", -1);
            LiteModQuarryBreaker.handleQuarryBreakerCommand(Arrays.copyOfRange(rawArgs, 1, rawArgs.length));
            ci.cancel();
        }
    }
}
