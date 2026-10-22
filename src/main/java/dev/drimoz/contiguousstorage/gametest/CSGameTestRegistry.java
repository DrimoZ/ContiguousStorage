package dev.drimoz.contiguousstorage.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registers the {@link GameTest} methods of {@link CSGameTests} the 1.21.5 way: each is a test
 * function, and a test instance runs it on its template. A batch is a test environment, which runs
 * the batch's {@link BeforeBatch} on setup and its {@link AfterBatch} on teardown - Minecraft groups
 * tests into batches by environment.
 *
 * <p>Only where game tests run at all: the dev runs and the game test server, never a release.
 */
public final class CSGameTestRegistry {

    private static final List<Method> TESTS = annotated(GameTest.class);

    private CSGameTestRegistry() {
    }

    public static void register(IEventBus modBus) {
        if (GameTestHooks.isGametestEnabled()) {
            modBus.addListener(CSGameTestRegistry::registerFunctions);
            modBus.addListener(CSGameTestRegistry::registerTests);
        }
    }

    private static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, helper -> helper.register(id("batch"), Batch.CODEC));
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach(method ->
                helper.register(id(name(method)), (Consumer<GameTestHelper>) test -> invoke(method, test))));
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> batches = new HashMap<>();
        for (Method method : TESTS) {
            GameTest test = method.getAnnotation(GameTest.class);
            Holder<TestEnvironmentDefinition<?>> batch = batches.computeIfAbsent(test.batch(),
                    name -> event.registerEnvironment(id(name.isEmpty() ? "default" : name), new Batch(name)));
            event.registerTest(id(name(method)), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id(name(method))),
                    new TestData<>(batch, id(test.template()), test.timeoutTicks(), 0, test.required())));
        }
    }

    /** A batch of tests: its setup and teardown are the old {@code @BeforeBatch} and {@code @AfterBatch}. */
    private record Batch(String name) implements TestEnvironmentDefinition<Unit> {
        static final MapCodec<Batch> CODEC = Codec.STRING.fieldOf("batch").xmap(Batch::new, Batch::name);

        @Override
        public Unit setup(ServerLevel level) {
            annotated(BeforeBatch.class).stream()
                    .filter(method -> method.getAnnotation(BeforeBatch.class).batch().equals(name))
                    .forEach(method -> invoke(method, level));
            return Unit.INSTANCE;
        }

        @Override
        public void teardown(ServerLevel level, Unit saved) {
            annotated(AfterBatch.class).stream()
                    .filter(method -> method.getAnnotation(AfterBatch.class).batch().equals(name))
                    .forEach(method -> invoke(method, level));
        }

        @Override
        public MapCodec<Batch> codec() {
            return CODEC;
        }
    }

    private static List<Method> annotated(Class<? extends Annotation> annotation) {
        return Arrays.stream(CSGameTests.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(annotation)).toList();
    }

    /** Calls a test or batch method, letting a failed assertion through as itself. */
    private static void invoke(Method method, Object argument) {
        try {
            method.invoke(null, argument);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code aDrawerIsLinked} to {@code a_drawer_is_linked}: identifiers are lower case. */
    private static String name(Method method) {
        return method.getName().replaceAll("([A-Z0-9])", "_$1").toLowerCase();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ContiguousStorage.MOD_ID, path);
    }
}
