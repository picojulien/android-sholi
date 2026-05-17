package name.soulayrol.rhaa.sholi.sync.credentials;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionAndroidXEncryptedPreferencesRuntime
        implements AndroidXEncryptedPreferencesRuntime {

    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ENCRYPTED_SHARED_PREFERENCES_CLASS =
            "androidx.security.crypto.EncryptedSharedPreferences";
    private static final String PREF_KEY_ENCRYPTION_SCHEME_CLASS =
            "androidx.security.crypto.EncryptedSharedPreferences$PrefKeyEncryptionScheme";
    private static final String PREF_VALUE_ENCRYPTION_SCHEME_CLASS =
            "androidx.security.crypto.EncryptedSharedPreferences$PrefValueEncryptionScheme";
    private static final String MASTER_KEY_CLASS = "androidx.security.crypto.MasterKey";
    private static final String MASTER_KEY_BUILDER_CLASS = "androidx.security.crypto.MasterKey$Builder";
    private static final String MASTER_KEY_SCHEME_CLASS = "androidx.security.crypto.MasterKey$KeyScheme";
    private static final String MASTER_KEYS_CLASS = "androidx.security.crypto.MasterKeys";
    private static final String KEYGEN_PARAMETER_SPEC_CLASS =
            "android.security.keystore.KeyGenParameterSpec";

    private static final String SCHEME_AES256_SIV = "AES256_SIV";
    private static final String SCHEME_AES256_GCM = "AES256_GCM";
    private static final String MASTER_KEY_SCHEME_AES256_GCM = "AES256_GCM";
    private static final String MASTER_KEY_SPEC_AES256_GCM = "AES256_GCM_SPEC";

    @Override
    public Object openEncryptedSharedPreferences(Object context, String fileName) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }

        try {
            return openWithMasterKey(context, fileName);
        } catch (ReflectiveOperationException ignored) {
            try {
                return openWithMasterKeys(context, fileName);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Could not initialize AndroidX EncryptedSharedPreferences", e);
            }
        }
    }

    private Object openWithMasterKey(Object context, String fileName)
            throws ReflectiveOperationException {
        Class<?> contextClass = Class.forName(CONTEXT_CLASS);
        Class<?> encryptedPreferencesClass = Class.forName(ENCRYPTED_SHARED_PREFERENCES_CLASS);
        Class<?> masterKeyClass = Class.forName(MASTER_KEY_CLASS);
        Class<?> masterKeyBuilderClass = Class.forName(MASTER_KEY_BUILDER_CLASS);
        Class<?> masterKeySchemeClass = Class.forName(MASTER_KEY_SCHEME_CLASS);
        Class<?> prefKeySchemeClass = Class.forName(PREF_KEY_ENCRYPTION_SCHEME_CLASS);
        Class<?> prefValueSchemeClass = Class.forName(PREF_VALUE_ENCRYPTION_SCHEME_CLASS);

        Constructor<?> builderConstructor = masterKeyBuilderClass.getConstructor(contextClass);
        Object builder = builderConstructor.newInstance(context);
        Method setKeyScheme = masterKeyBuilderClass.getMethod("setKeyScheme", masterKeySchemeClass);
        Object keyScheme = enumValue(masterKeySchemeClass, MASTER_KEY_SCHEME_AES256_GCM);
        setKeyScheme.invoke(builder, keyScheme);
        Method build = masterKeyBuilderClass.getMethod("build");
        Object masterKey = build.invoke(builder);

        Method create = encryptedPreferencesClass.getMethod(
                "create",
                contextClass,
                String.class,
                masterKeyClass,
                prefKeySchemeClass,
                prefValueSchemeClass);

        Object keyEncryptionScheme = enumValue(prefKeySchemeClass, SCHEME_AES256_SIV);
        Object valueEncryptionScheme = enumValue(prefValueSchemeClass, SCHEME_AES256_GCM);
        return create.invoke(
                null,
                context,
                fileName,
                masterKey,
                keyEncryptionScheme,
                valueEncryptionScheme);
    }

    private Object openWithMasterKeys(Object context, String fileName)
            throws ReflectiveOperationException {
        Class<?> contextClass = Class.forName(CONTEXT_CLASS);
        Class<?> encryptedPreferencesClass = Class.forName(ENCRYPTED_SHARED_PREFERENCES_CLASS);
        Class<?> masterKeysClass = Class.forName(MASTER_KEYS_CLASS);
        Class<?> keygenSpecClass = Class.forName(KEYGEN_PARAMETER_SPEC_CLASS);
        Class<?> prefKeySchemeClass = Class.forName(PREF_KEY_ENCRYPTION_SCHEME_CLASS);
        Class<?> prefValueSchemeClass = Class.forName(PREF_VALUE_ENCRYPTION_SCHEME_CLASS);

        Field specField = masterKeysClass.getField(MASTER_KEY_SPEC_AES256_GCM);
        Object spec = specField.get(null);
        Method getOrCreate = masterKeysClass.getMethod("getOrCreate", keygenSpecClass);
        String masterKeyAlias = (String) getOrCreate.invoke(null, spec);

        Method create = encryptedPreferencesClass.getMethod(
                "create",
                String.class,
                String.class,
                contextClass,
                prefKeySchemeClass,
                prefValueSchemeClass);

        Object keyEncryptionScheme = enumValue(prefKeySchemeClass, SCHEME_AES256_SIV);
        Object valueEncryptionScheme = enumValue(prefValueSchemeClass, SCHEME_AES256_GCM);
        return create.invoke(
                null,
                fileName,
                masterKeyAlias,
                context,
                keyEncryptionScheme,
                valueEncryptionScheme);
    }

    private Object enumValue(Class<?> enumClass, String constantName) throws NoSuchFieldException,
            IllegalAccessException {
        Field field = enumClass.getField(constantName);
        return field.get(null);
    }
}
