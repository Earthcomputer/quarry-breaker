package net.earthcomputer.quarrybreaker.mixin;

import net.earthcomputer.quarrybreaker.LiteModQuarryBreaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.TabCompleter;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

@Mixin(TabCompleter.class)
public abstract class MixinTabCompleter {
    @Shadow protected boolean requestedCompletions;

    @Shadow @Final protected GuiTextField textField;

    @Shadow protected int completionIdx;

    @Shadow protected List<String> completions;

    @Shadow public abstract void setCompletions(String... newCompl);

    @Shadow @Nullable public abstract BlockPos getTargetBlockPos();

    @Inject(method = "requestCompletions", at = @At("HEAD"), cancellable = true)
    private void onRequestCompletions(String prefix, CallbackInfo ci) {
        if (prefix.startsWith("/quarrybreaker")) {
            requestedCompletions = true;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                String[] rawArgs = prefix.split(" ", -1);
                String[] newCompl = LiteModQuarryBreaker.tabCompleteQuarryBreakerCommand(
                        Arrays.copyOfRange(rawArgs, 1, rawArgs.length), getTargetBlockPos()
                ).toArray(new String[0]);
                Arrays.sort(newCompl);
                setCompletions(newCompl);
            });
            ci.cancel();
        }
    }

    @ModifyVariable(method = "setCompletions", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String[] modifyCommandCompletions(String[] completions) {
        String prefix = textField.getText().substring(0, textField.getCursorPosition());
        if (!prefix.contains(" ")) {
            if ("/quarrybreaker".startsWith(prefix) && !ArrayUtils.contains(completions, "/quarrybreaker")) {
                completions = ArrayUtils.add(completions, "/quarrybreaker");
                Arrays.sort(completions);
            }
        }
        return completions;
    }

    @Inject(method = "complete", at = @At("TAIL"))
    private void hackFixCrashIdk(CallbackInfo ci) {
        if (completionIdx >= completions.size()) {
            completionIdx = 0;
        }
    }
}
