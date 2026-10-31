/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testutils;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Annotate a test with this annotation to indicate
 * that the test requires permissions to create symlinks.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresSymlink.Condition.class)
public @interface RequiresSymlink {
    class Condition implements ExecutionCondition {
        private static final boolean CAN_CREATE = canCreateSymlinks();

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (CAN_CREATE) {
                return ConditionEvaluationResult.enabled("Symlink creation is allowed");
            } else {
                return ConditionEvaluationResult.disabled("Current user cannot create symbolic links");
            }
        }

        private static boolean canCreateSymlinks() {
            Path target = null;
            Path link = null;
            try {
                target = Files.createTempFile("symlink_target", ".tmp");
                link = target.getParent().resolve("symlink_test.tmp");

                Files.createSymbolicLink(link, target);
                return true;
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                return false;
            } finally {
                try {
                    if (link != null && Files.exists(link)) Files.delete(link);
                    if (target != null && Files.exists(target)) Files.delete(target);
                } catch (IOException ignored) {}
            }
        }
    }
}
