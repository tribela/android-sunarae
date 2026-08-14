/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kjwon15.noshiftkeyboard.latin.settings;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import net.kjwon15.noshiftkeyboard.R;
import net.kjwon15.noshiftkeyboard.latin.utils.ApplicationUtils;

public final class SettingsFragment extends InputMethodSettingsFragment {
    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.setTitle(
                ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));
        final Resources res = getResources();

        findPreference("privacy_policy").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showTextDialog(res.getString(R.string.privacy_policy),
                        res.getString(R.string.privacy_policy_text));
                return true;
            }
        });
        findPreference("license").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showTextDialog(res.getString(R.string.license),
                        loadRawResource(R.raw.license_text));
                return true;
            }
        });
    }

    private void showTextDialog(final String title, final String text) {
        new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String loadRawResource(final int resId) {
        try {
            final java.io.InputStream is = getResources().openRawResource(resId);
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }
            is.close();
            return bos.toString("UTF-8");
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
