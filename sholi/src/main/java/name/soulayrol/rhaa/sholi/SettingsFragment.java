/*
 * ShoLi, a simple tool to produce short (shopping) lists.
 *
 * Copyright (C) 2014,2015  David Soulayrol
 *
 * ShoLi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ShoLi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package name.soulayrol.rhaa.sholi;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import name.soulayrol.rhaa.sholi.sync.ProductionCredentialStores;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialSafeText;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;
import name.soulayrol.rhaa.sholi.sync.settings.KeyValueWebDavSyncProfileStore;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavEndpointValidationResult;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavEndpointValidator;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavRemotePathSecurity;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavSyncProfileStore;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavUrlSecurity;
import name.soulayrol.rhaa.sholi.sync.webdav.HttpUrlConnectionWebDavTransport;


public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);

        Preference.OnPreferenceChangeListener c = new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object value) {
                return checkImportMarkers(preference.getKey(), (String)value);
            }
        };

        findPreference(SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED).setOnPreferenceChangeListener(c);
        findPreference(SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED).setOnPreferenceChangeListener(c);
        findPreference(SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST).setOnPreferenceChangeListener(c);

        findPreference(SettingsActivity.KEY_WEBDAV_URL).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object value) {
                        return validateWebDavUrl((String) value);
                    }
                });
        findPreference(SettingsActivity.KEY_WEBDAV_REMOTE_PATH).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object value) {
                        return validateWebDavRemotePath((String) value);
                    }
                });
        findPreference(SettingsActivity.KEY_WEBDAV_PASSWORD_TOKEN).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object value) {
                        return saveWebDavPasswordOrToken((String) value);
                    }
                });
        findPreference(SettingsActivity.KEY_WEBDAV_TEST_CONNECTION).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        testWebDavConnection(false);
                        return true;
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_CHECKING_FLING_LEFT_ACTION);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_CHECKING_FLING_RIGHT_ACTION);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_LIST_ITEM_SIZE);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_IMPORT_MERGE_POLICY);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_URL);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_USERNAME);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_PASSWORD_TOKEN);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_REMOTE_PATH);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_DISPLAY_NAME);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_TEST_CONNECTION);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreferenceSummary(sharedPreferences, key);
    }

    private boolean checkImportMarkers(String key, String value) {
        boolean result = false;

        if (!value.trim().isEmpty()) {
            // Check all markers are different.
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            Set<String> set = new HashSet<String>();

            set.add(value);
            if (!key.equals(SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED))
                set.add(sharedPreferences.getString(SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED, ""));
            if (!key.equals(SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED))
                set.add(sharedPreferences.getString(SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED, ""));
            if (!key.equals(SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST))
                set.add(sharedPreferences.getString(SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST, ""));

            result = (set.size() == 3);
        }

        if (!result)
            Toast.makeText(getActivity(),
                    getResources().getString(R.string.fragment_settings_marker_error),
                    Toast.LENGTH_SHORT).show();

        return result;
    }

    private boolean validateWebDavUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            WebDavUrlSecurity.requireSafeForStorage(value);
        } catch (IllegalArgumentException e) {
            Toast.makeText(
                    getActivity(),
                    getResources().getString(R.string.settings_webdav_url_rejected_secret),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (value.trim().toLowerCase().startsWith("http://")) {
            Toast.makeText(
                    getActivity(),
                    getResources().getString(R.string.settings_webdav_non_https_warning),
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private boolean validateWebDavRemotePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            WebDavRemotePathSecurity.requireSafeForStorage(value);
        } catch (IllegalArgumentException e) {
            Toast.makeText(
                    getActivity(),
                    getResources().getString(R.string.settings_webdav_remote_path_rejected_secret),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private boolean saveWebDavPasswordOrToken(String value) {
        try {
            CredentialStore store = ProductionCredentialStores.webDav(getActivity());
            if (value == null || value.length() == 0) {
                store.clear();
                Toast.makeText(
                        getActivity(),
                        getResources().getString(R.string.settings_webdav_token_cleared),
                        Toast.LENGTH_SHORT).show();
            } else {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(getActivity());
                String username = sharedPreferences.getString(SettingsActivity.KEY_WEBDAV_USERNAME, "");
                store.save(new WebDavCredentials(username, value));
                Toast.makeText(
                        getActivity(),
                        getResources().getString(R.string.settings_webdav_token_saved),
                        Toast.LENGTH_SHORT).show();
            }
            updatePreferenceSummary(
                    PreferenceManager.getDefaultSharedPreferences(getActivity()),
                    SettingsActivity.KEY_WEBDAV_PASSWORD_TOKEN);
            // Do not let EditTextPreference retain secret text in memory or preferences.
            return false;
        } catch (RuntimeException e) {
            Toast.makeText(
                    getActivity(),
                    getResources().getString(R.string.settings_webdav_token_unavailable),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void testWebDavConnection(final boolean allowInsecureUrl) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        WebDavSyncProfile profile;
        try {
            profile = profileStore(sharedPreferences).load();
            if (profile == null) {
                Toast.makeText(
                        getActivity(),
                        getResources().getString(R.string.settings_webdav_incomplete),
                        Toast.LENGTH_LONG).show();
                return;
            }
        } catch (RuntimeException e) {
            Toast.makeText(
                    getActivity(),
                    getResources().getString(R.string.settings_webdav_incomplete),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final WebDavSyncProfile configuredProfile = profile;

        WebDavCredentials credentials;
        try {
            credentials = ProductionCredentialStores.webDav(activity).load();
        } catch (RuntimeException e) {
            Toast.makeText(
                    activity,
                    getResources().getString(R.string.settings_webdav_token_unavailable),
                    Toast.LENGTH_LONG).show();
            return;
        }

        final WebDavCredentials configuredCredentials = credentials;
        new Thread(new Runnable() {
            @Override
            public void run() {
                WebDavEndpointValidationResult validationResult;
                try {
                    validationResult = new WebDavEndpointValidator(
                            new HttpUrlConnectionWebDavTransport()).validate(
                                    configuredProfile,
                                    configuredCredentials,
                                    allowInsecureUrl);
                } catch (RuntimeException e) {
                    validationResult = WebDavEndpointValidationResult.failure(
                            WebDavEndpointValidationResult.Status.NETWORK_ERROR,
                            "Network error while testing the WebDAV endpoint.");
                }
                final WebDavEndpointValidationResult result = validationResult;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onWebDavConnectionTestResult(sharedPreferences, configuredProfile, result);
                    }
                });
            }
        }, "sholi-webdav-test").start();
    }

    private void onWebDavConnectionTestResult(
            SharedPreferences sharedPreferences,
            WebDavSyncProfile profile,
            WebDavEndpointValidationResult result) {
        if (!isAdded()) {
            return;
        }
        if (result.getStatus()
                == WebDavEndpointValidationResult.Status.INSECURE_URL_REQUIRES_CONFIRMATION) {
            confirmInsecureWebDavTest();
            return;
        }

        String safeMessage = CredentialSafeText.message(result.getMessage());
        WebDavSyncProfileStore store = profileStore(sharedPreferences);
        WebDavSyncProfile currentProfile;
        try {
            currentProfile = store.load();
        } catch (RuntimeException e) {
            return;
        }
        if (currentProfile == null || !sameTestedWebDavProfile(profile, currentProfile)) {
            return;
        }
        store.saveTestResult(result.getStatus().name(), safeMessage);
        updatePreferenceSummary(sharedPreferences, SettingsActivity.KEY_WEBDAV_TEST_CONNECTION);
        Toast.makeText(getActivity(), safeMessage, Toast.LENGTH_LONG).show();
    }

    private void confirmInsecureWebDavTest() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.settings_webdav_non_https_confirm_title)
                .setMessage(R.string.settings_webdav_non_https_warning)
                .setPositiveButton(
                        R.string.settings_webdav_non_https_confirm_action,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                testWebDavConnection(true);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updatePreferenceSummary(SharedPreferences sharedPreferences, String key) {
        if (SettingsActivity.isListPreference(key)) {
            ListPreference p = (ListPreference) findPreference(key);
            p.setSummary(p.getEntry());
        } else if (SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED.equals(key)
                || SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED.equals(key)
                || SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST.equals(key)) {
            Preference p = findPreference(key);
            p.setSummary(sharedPreferences.getString(key, ""));
        } else if (SettingsActivity.KEY_WEBDAV_URL.equals(key)) {
            Preference p = findPreference(key);
            p.setSummary(safeWebDavUrlSummary(sharedPreferences));
        } else if (SettingsActivity.KEY_WEBDAV_USERNAME.equals(key)
                || SettingsActivity.KEY_WEBDAV_DISPLAY_NAME.equals(key)) {
            Preference p = findPreference(key);
            p.setSummary(sharedPreferences.getString(key, ""));
        } else if (SettingsActivity.KEY_WEBDAV_REMOTE_PATH.equals(key)) {
            Preference p = findPreference(key);
            p.setSummary(safeWebDavRemotePathSummary(sharedPreferences));
        } else if (SettingsActivity.KEY_WEBDAV_PASSWORD_TOKEN.equals(key)) {
            Preference p = findPreference(key);
            p.setSummary(getResources().getString(R.string.settings_webdav_token_summary_redacted));
        } else if (SettingsActivity.KEY_WEBDAV_TEST_CONNECTION.equals(key)
                || SettingsActivity.KEY_WEBDAV_LAST_TEST_MESSAGE.equals(key)) {
            Preference p = findPreference(SettingsActivity.KEY_WEBDAV_TEST_CONNECTION);
            p.setSummary(sharedPreferences.getString(
                    SettingsActivity.KEY_WEBDAV_LAST_TEST_MESSAGE,
                    getResources().getString(R.string.settings_webdav_test_connection_summary)));
        }
    }

    private String safeWebDavUrlSummary(SharedPreferences sharedPreferences) {
        String url = sharedPreferences.getString(SettingsActivity.KEY_WEBDAV_URL, "");
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        try {
            WebDavUrlSecurity.requireSafeForStorage(url);
        } catch (IllegalArgumentException e) {
            sharedPreferences.edit()
                    .remove(SettingsActivity.KEY_WEBDAV_URL)
                    .remove(SettingsActivity.KEY_WEBDAV_LAST_TEST_STATUS)
                    .remove(SettingsActivity.KEY_WEBDAV_LAST_TEST_MESSAGE)
                    .apply();
            return getResources().getString(R.string.settings_webdav_url_summary);
        }
        return CredentialSafeText.url(url);
    }

    private String safeWebDavRemotePathSummary(SharedPreferences sharedPreferences) {
        String remotePath = sharedPreferences.getString(SettingsActivity.KEY_WEBDAV_REMOTE_PATH, "");
        if (remotePath == null || remotePath.trim().isEmpty()) {
            return "";
        }
        try {
            return WebDavRemotePathSecurity.requireSafeForStorage(remotePath);
        } catch (IllegalArgumentException e) {
            sharedPreferences.edit()
                    .remove(SettingsActivity.KEY_WEBDAV_REMOTE_PATH)
                    .remove(SettingsActivity.KEY_WEBDAV_LAST_TEST_STATUS)
                    .remove(SettingsActivity.KEY_WEBDAV_LAST_TEST_MESSAGE)
                    .apply();
            return getResources().getString(R.string.settings_webdav_remote_path_summary);
        }
    }

    private boolean sameTestedWebDavProfile(WebDavSyncProfile tested, WebDavSyncProfile current) {
        return tested.getUrl().equals(current.getUrl())
                && tested.getUsername().equals(current.getUsername())
                && tested.getRemotePath().equals(current.getRemotePath())
                && tested.getDisplayName().equals(current.getDisplayName())
                && equalsNullable(tested.getClientId(), current.getClientId());
    }

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private WebDavSyncProfileStore profileStore(SharedPreferences sharedPreferences) {
        return new KeyValueWebDavSyncProfileStore(new SharedPreferencesKeyValueStore(sharedPreferences));
    }

    private static final class SharedPreferencesKeyValueStore
            implements KeyValueWebDavSyncProfileStore.KeyValueStore {

        private final SharedPreferences sharedPreferences;

        SharedPreferencesKeyValueStore(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public String getString(String key, String defaultValue) {
            return sharedPreferences.getString(key, defaultValue);
        }

        @Override
        public void putString(String key, String value) {
            sharedPreferences.edit().putString(key, value).apply();
        }

        @Override
        public void remove(String key) {
            sharedPreferences.edit().remove(key).apply();
        }
    }
}
