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

package androidx.camera.extensions.impl;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;

/**
 * Stub implementation for night preview use case.
 *
 * <p>This class should be implemented by OEM and deployed to the target devices.
 */
public final class NightPreviewExtenderImpl implements PreviewExtenderImpl {
    public NightPreviewExtenderImpl() {
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public void enableExtension(String cameraId, CameraCharacteristics cameraCharacteristics) {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public CaptureStageImpl getCaptureStage() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public ProcessorType getProcessorType() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public ProcessorImpl getProcessor() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public void onInit(String cameraId, CameraCharacteristics cameraCharacteristics,
            Context context) {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public void onDeInit() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public CaptureStageImpl onPresetSession() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public CaptureStageImpl onEnableSession() {
        throw new RuntimeException("Stub, replace with implementation.");
    }

    @Override
    public CaptureStageImpl onDisableSession() {
        throw new RuntimeException("Stub, replace with implementation.");
    }
}
