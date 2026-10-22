package dev.drimoz.contiguousstorage.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A game test, as {@code @GameTest} was before 1.21.5 - Minecraft now registers tests as registry
 * entries, and {@link CSGameTestRegistry} turns these methods into them. Same attributes, same
 * defaults, so the tests read as they did on 1.21.1.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** Under {@code data/contiguousstorage/structure}. */
    String template();

    /** Tests of one batch share a {@link BeforeBatch} and an {@link AfterBatch}. */
    String batch() default "";

    boolean required() default true;

    int timeoutTicks() default 100;
}
