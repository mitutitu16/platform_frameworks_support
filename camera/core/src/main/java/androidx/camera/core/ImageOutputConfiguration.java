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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Configuration containing options for configuring the output image data of a pipeline. */
public interface ImageOutputConfiguration extends Configuration.Reader {
  /**
   * Retrieves the aspect ratio of the target intending to use images from this configuration.
   *
   * <p>This is the ratio of the target's width to the image's height, where the numerator of the
   * provided {@link Rational} corresponds to the width, and the denominator corresponds to the
   * height.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   */
  @Nullable
  default Rational getTargetAspectRatio(@Nullable Rational valueIfMissing) {
    return retrieveOption(OPTION_TARGET_ASPECT_RATIO, valueIfMissing);
  }

  /**
   * Retrieves the aspect ratio of the target intending to use images from this configuration.
   *
   * <p>This is the ratio of the target's width to the image's height, where the numerator of the
   * provided {@link Rational} corresponds to the width, and the denominator corresponds to the
   * height.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  default Rational getTargetAspectRatio() {
    return retrieveOption(OPTION_TARGET_ASPECT_RATIO);
  }

  /**
   * Retrieves the rotation of the target intending to use images from this configuration.
   *
   * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
   * {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}. Rotation values are relative to the
   * device's "natural" rotation, {@link Surface#ROTATION_0}.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   */
  @RotationValue
  default int getTargetRotation(int valueIfMissing) {
    return retrieveOption(OPTION_TARGET_ROTATION, valueIfMissing);
  }

  /**
   * Retrieves the rotation of the target intending to use images from this configuration.
   *
   * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
   * {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}. Rotation values are relative to the
   * device's "natural" rotation, {@link Surface#ROTATION_0}.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @RotationValue
  default int getTargetRotation() {
    return retrieveOption(OPTION_TARGET_ROTATION);
  }

  /**
   * Retrieves the resolution of the target intending to use from this configuration.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  default Size getTargetResolution(Size valueIfMissing) {
    return retrieveOption(OPTION_TARGET_RESOLUTION, valueIfMissing);
  }

  /**
   * Retrieves the resolution of the target intending to use from this configuration.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  default Size getTargetResolution() {
    return retrieveOption(OPTION_TARGET_RESOLUTION);
  }

  /**
   * Retrieves the max resolution limitation of the target intending to use from this configuration.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
   *     configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  default Size getMaxResolution(Size valueIfMissing) {
    return retrieveOption(OPTION_MAX_RESOLUTION, valueIfMissing);
  }

  /**
   * Retrieves the max resolution limitation of the target intending to use from this configuration.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  default Size getMaxResolution() {
    return retrieveOption(OPTION_MAX_RESOLUTION);
  }

  /**
   * Builder for a {@link ImageOutputConfiguration}.
   *
   * @param <C> The top level configuration which will be generated by {@link #build()}.
   * @param <T> The top level builder type for which this builder is composed with.
   */
  interface Builder<C extends Configuration, T extends Builder<C, T>>
      extends Configuration.Builder<C, T> {

    /**
     * Sets the aspect ratio of the intended target for images from this configuration.
     *
     * <p>This is the ratio of the target's width to the image's height, where the numerator of the
     * provided {@link Rational} corresponds to the width, and the denominator corresponds to the
     * height.
     *
     * @param aspectRatio A {@link Rational} representing the ratio of the target's width and
     *     height.
     * @return The current Builder.
     */
    default T setTargetAspectRatio(Rational aspectRatio) {
      getMutableConfiguration().insertOption(OPTION_TARGET_ASPECT_RATIO, aspectRatio);
      return builder();
    }

    /**
     * Sets the rotation of the intended target for images from this configuration.
     *
     * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
     * {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}. Rotation values are relative to
     * the "natural" rotation, {@link Surface#ROTATION_0}.
     *
     * @param rotation The rotation of the intended target.
     * @return The current Builder.
     */
    default T setTargetRotation(@RotationValue int rotation) {
      getMutableConfiguration().insertOption(OPTION_TARGET_ROTATION, rotation);
      return builder();
    }

    /**
     * Sets the resolution of the intended target from this configuration.
     *
     * @param resolution The target resolution to choose from supported output sizes list.
     * @return The current Builder.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    default T setTargetResolution(Size resolution) {
      getMutableConfiguration().insertOption(OPTION_TARGET_RESOLUTION, resolution);
      return builder();
    }

    /**
     * Sets the max resolution limitation of the intended target from this configuration.
     *
     * @param resolution The max resolution limitation to choose from supported output sizes list.
     * @return The current Builder.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    default T setMaxResolution(Size resolution) {
      getMutableConfiguration().insertOption(OPTION_MAX_RESOLUTION, resolution);
      return builder();
    }
  }

  /**
   * Valid integer rotation values.
   *
   * @hide
   */
  @IntDef({Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270})
  @Retention(RetentionPolicy.SOURCE)
  @interface RotationValue {}

  /**
   * Invalid integer rotation.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  int INVALID_ROTATION = -1;

  // Option Declarations:
  // ***********************************************************************************************

  /**
   * Option: camerax.core.imageOutput.targetAspectRatio
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<Rational> OPTION_TARGET_ASPECT_RATIO =
      Option.create("camerax.core.imageOutput.targetAspectRatio", Rational.class);

  /**
   * Option: camerax.core.imageOutput.targetRotation
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<Integer> OPTION_TARGET_ROTATION =
      Option.create("camerax.core.imageOutput.targetRotation", int.class);

  /**
   * Option: camerax.core.imageOutput.targetResolution
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<Size> OPTION_TARGET_RESOLUTION =
      Option.create("camerax.core.imageOutput.targetResolution", Size.class);

  /**
   * Option: camerax.core.imageOutput.maxResolution
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  Option<Size> OPTION_MAX_RESOLUTION =
      Option.create("camerax.core.imageOutput.maxResolution", Size.class);
}