/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.extensions;

import android.util.Log;

import androidx.camera.core.PreviewConfig;
import androidx.camera.extensions.impl.AutoPreviewExtenderImpl;

/**
 * Load the OEM extension Preview implementation for auto effect type.
 */
public class AutoPreviewExtender extends PreviewExtender {
    private static final String TAG = "AutoPreviewExtender";

    /**
     * Create a new instance of the auto extender.
     *
     * @param builder Builder that will be used to create the configurations for the
     * {@link androidx.camera.core.Preview}.
     */
    public static AutoPreviewExtender create(PreviewConfig.Builder builder) {
        if (ExtensionVersion.isExtensionVersionSupported()) {
            try {
                return new VendorAutoPreviewExtender(builder);
            } catch (NoClassDefFoundError e) {
                Log.d(TAG, "No auto preview extender found. Falling back to default.");
            }
        }

        return new DefaultAutoPreviewExtender();
    }

    /** Empty implementation of auto extender which does nothing. */
    static class DefaultAutoPreviewExtender extends AutoPreviewExtender {
        DefaultAutoPreviewExtender() {
        }

        @Override
        public boolean isExtensionAvailable() {
            return false;
        }

        @Override
        public void enableExtension() {
        }
    }

    /** Auto extender that calls into the vendor provided implementation. */
    static class VendorAutoPreviewExtender extends AutoPreviewExtender {
        private final AutoPreviewExtenderImpl mImpl;

        VendorAutoPreviewExtender(PreviewConfig.Builder builder) {
            mImpl = new AutoPreviewExtenderImpl();
            init(builder, mImpl);
        }
    }

    private AutoPreviewExtender() {}
}
