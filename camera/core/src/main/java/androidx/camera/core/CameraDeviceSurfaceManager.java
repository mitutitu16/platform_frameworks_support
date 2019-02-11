/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import androidx.annotation.Nullable;
import android.util.Size;
import java.util.List;
import java.util.Map;

/**
 * Camera device manager to provide the guaranteed supported stream capabilities related info for
 * all camera devices
 *
 * @hide
 */
public interface CameraDeviceSurfaceManager {
  /**
   * Check whether the input surface configuration list is under the capability of any combination
   * of this object.
   *
   * @param cameraId the camera id of the camera device to be compared
   * @param surfaceConfigurationList the surface configuration list to be compared
   * @return the check result that whether it could be supported
   */
  boolean checkSupported(String cameraId, List<SurfaceConfiguration> surfaceConfigurationList);

  /**
   * Transform to a SurfaceConfiguration object with cameraId, image format and size info
   *
   * @param cameraId the camera id of the camera device to transform the object
   * @param imageFormat the image format info for the surface configuration object
   * @param size the size info for the surface configuration object
   * @return new {@link SurfaceConfiguration} object
   */
  SurfaceConfiguration transformSurfaceConfiguration(String cameraId, int imageFormat, Size size);

  /**
   * Get max supported output size for specific camera device and image format
   *
   * @param cameraId the camera Id
   * @param imageFormat the image format info
   * @return the max supported output size for the image format
   */
  @Nullable
  Size getMaxOutputSize(String cameraId, int imageFormat);

  /**
   * Retrieves a map of suggested resolutions for the given list of use cases.
   *
   * @param cameraId the camera id of the camera device used by the use cases
   * @param originalUseCases list of use cases with existing surfaces
   * @param newUseCases list of new use cases
   * @return map of suggested resolutions for given use cases
   */
  Map<BaseUseCase, Size> getSuggestedResolutions(
      String cameraId, List<BaseUseCase> originalUseCases, List<BaseUseCase> newUseCases);

  /**
   * Retrieves the preview size, choosing the smaller of the display size and 1080P.
   *
   * @return the size used for the on screen preview
   */
  Size getPreviewSize();
}