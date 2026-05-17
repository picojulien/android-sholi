package name.soulayrol.rhaa.sholi.sync.credentials;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class AndroidXEncryptedSharedPreferencesFactory implements EncryptedPreferencesFactory {

    private final Object context;
    private final AndroidXEncryptedPreferencesRuntime runtime;

    public AndroidXEncryptedSharedPreferencesFactory(Object context) {
        this(context, new ReflectionAndroidXEncryptedPreferencesRuntime());
    }

    public AndroidXEncryptedSharedPreferencesFactory(
            Object context,
            AndroidXEncryptedPreferencesRuntime runtime) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.context = context;
        this.runtime = runtime;
    }

    @Override
    public String storageEngineClassName() {
        return EncryptedSharedPreferencesCredentialStore.ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS;
    }

    @Override
    public EncryptedPreferences open(String fileName) {
        Object preferences = runtime.openEncryptedSharedPreferences(context, fileName);
        return new ReflectiveEncryptedPreferences(preferences);
    }

    private static final class ReflectiveEncryptedPreferences implements EncryptedPreferences {
        private final Object sharedPreferences;

        private ReflectiveEncryptedPreferences(Object sharedPreferences) {
            if (sharedPreferences == null) {
                throw new IllegalArgumentException("sharedPreferences must not be null");
            }
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public void putString(String key, String value) {
            Object editor = invoke(sharedPreferences, "edit");
            invoke(editor, "putString", new Class<?>[] { String.class, String.class }, key, value);
            invoke(editor, "apply");
        }

        @Override
        public String getString(String key) {
            return (String) invoke(
                    sharedPreferences,
                    "getString",
                    new Class<?>[] { String.class, String.class },
                    key,
                    null);
        }

        @Override
        public void remove(String key) {
            Object editor = invoke(sharedPreferences, "edit");
            invoke(editor, "remove", new Class<?>[] { String.class }, key);
            invoke(editor, "apply");
        }

        private static Object invoke(Object target, String methodName) {
            return invoke(target, methodName, new Class<?>[0]);
        }

        private static Object invoke(
                Object target,
                String methodName,
                Class<?>[] parameterTypes,
                Object... args) {
            try {
                Method method = target.getClass().getMethod(methodName, parameterTypes);
                return method.invoke(target, args);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Missing method: " + methodName, e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Method not accessible: " + methodName, e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Method invocation failed: " + methodName, e);
            }
        }
    }
}
