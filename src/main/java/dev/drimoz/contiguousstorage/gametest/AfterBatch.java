package dev.drimoz.contiguousstorage.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runs after the tests of {@link #batch()}, with the {@code ServerLevel}. See {@link GameTest}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterBatch {
    String batch();
}
