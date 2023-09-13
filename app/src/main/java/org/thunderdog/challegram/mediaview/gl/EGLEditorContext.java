/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview.gl;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Message;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

public class EGLEditorContext {

  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  private static final int EGL_OPENGL_ES2_BIT = 4;

  private static final String radialBlurFragmentShaderCode =
    "varying highp vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "uniform sampler2D inputImageTexture2;" +
      "uniform lowp float excludeSize;" +
      "uniform lowp vec2 excludePoint;" +
      "uniform lowp float excludeBlurSize;" +
      "uniform highp float aspectRatio;" +
      "void main() {" +
      "lowp vec4 sharpImageColor = texture2D(sourceImage, texCoord);" +
      "lowp vec4 blurredImageColor = texture2D(inputImageTexture2, texCoord);" +
      "highp vec2 texCoordToUse = vec2(texCoord.x, (texCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));" +
      "highp float distanceFromCenter = distance(excludePoint, texCoordToUse);" +
      "gl_FragColor = mix(sharpImageColor, blurredImageColor, smoothstep(excludeSize - excludeBlurSize, excludeSize, distanceFromCenter));" +
      "}";

  private static final String linearBlurFragmentShaderCode =
    "varying highp vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "uniform sampler2D inputImageTexture2;" +
      "uniform lowp float excludeSize;" +
      "uniform lowp vec2 excludePoint;" +
      "uniform lowp float excludeBlurSize;" +
      "uniform highp float angle;" +
      "uniform highp float aspectRatio;" +
      "void main() {" +
      "lowp vec4 sharpImageColor = texture2D(sourceImage, texCoord);" +
      "lowp vec4 blurredImageColor = texture2D(inputImageTexture2, texCoord);" +
      "highp vec2 texCoordToUse = vec2(texCoord.x, (texCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));" +
      "highp float distanceFromCenter = abs((texCoordToUse.x - excludePoint.x) * aspectRatio * cos(angle) + (texCoordToUse.y - excludePoint.y) * sin(angle));" +
      "gl_FragColor = mix(sharpImageColor, blurredImageColor, smoothstep(excludeSize - excludeBlurSize, excludeSize, distanceFromCenter));" +
      "}";

  private static final String blurVertexShaderCode =
    "attribute vec4 position;" +
      "attribute vec4 inputTexCoord;" +
      "uniform highp float texelWidthOffset;" +
      "uniform highp float texelHeightOffset;" +
      "varying vec2 blurCoordinates[9];" +
      "void main() {" +
      "gl_Position = position;" +
      "vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);" +
      "blurCoordinates[0] = inputTexCoord.xy;" +
      "blurCoordinates[1] = inputTexCoord.xy + singleStepOffset * 1.458430;" +
      "blurCoordinates[2] = inputTexCoord.xy - singleStepOffset * 1.458430;" +
      "blurCoordinates[3] = inputTexCoord.xy + singleStepOffset * 3.403985;" +
      "blurCoordinates[4] = inputTexCoord.xy - singleStepOffset * 3.403985;" +
      "blurCoordinates[5] = inputTexCoord.xy + singleStepOffset * 5.351806;" +
      "blurCoordinates[6] = inputTexCoord.xy - singleStepOffset * 5.351806;" +
      "blurCoordinates[7] = inputTexCoord.xy + singleStepOffset * 7.302940;" +
      "blurCoordinates[8] = inputTexCoord.xy - singleStepOffset * 7.302940;" +
      "}";

  private static final String blurFragmentShaderCode =
    "uniform sampler2D sourceImage;" +
      "varying highp vec2 blurCoordinates[9];" +
      "void main() {" +
      "lowp vec4 sum = vec4(0.0);" +
      "sum += texture2D(sourceImage, blurCoordinates[0]) * 0.133571;" +
      "sum += texture2D(sourceImage, blurCoordinates[1]) * 0.233308;" +
      "sum += texture2D(sourceImage, blurCoordinates[2]) * 0.233308;" +
      "sum += texture2D(sourceImage, blurCoordinates[3]) * 0.135928;" +
      "sum += texture2D(sourceImage, blurCoordinates[4]) * 0.135928;" +
      "sum += texture2D(sourceImage, blurCoordinates[5]) * 0.051383;" +
      "sum += texture2D(sourceImage, blurCoordinates[6]) * 0.051383;" +
      "sum += texture2D(sourceImage, blurCoordinates[7]) * 0.012595;" +
      "sum += texture2D(sourceImage, blurCoordinates[8]) * 0.012595;" +
      "gl_FragColor = sum;" +
      "}";

  private static final String rgbToHsvFragmentShaderCode =
    "precision highp float;" +
      "varying vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "vec3 rgb_to_hsv(vec3 c) {" +
      "vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);" +
      "vec4 p = c.g < c.b ? vec4(c.bg, K.wz) : vec4(c.gb, K.xy);" +
      "vec4 q = c.r < p.x ? vec4(p.xyw, c.r) : vec4(c.r, p.yzx);" +
      "float d = q.x - min(q.w, q.y);" +
      "float e = 1.0e-10;" +
      "return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);" +
      "}" +
      "void main() {" +
      "vec4 texel = texture2D(sourceImage, texCoord);" +
      "gl_FragColor = vec4(rgb_to_hsv(texel.rgb), texel.a);" +
      "}";

  private static final String enhanceFragmentShaderCode =
    "precision highp float;" +
      "varying vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "uniform sampler2D inputImageTexture2;" +
      "uniform float intensity;" +
      "float enhance(float value) {" +
      "const vec2 offset = vec2(0.001953125, 0.03125);" +
      "value = value + offset.x;" +
      "vec2 coord = (clamp(texCoord, 0.125, 1.0 - 0.125001) - 0.125) * 4.0;" +
      "vec2 frac = fract(coord);" +
      "coord = floor(coord);" +
      "float p00 = float(coord.y * 4.0 + coord.x) * 0.0625 + offset.y;" +
      "float p01 = float(coord.y * 4.0 + coord.x + 1.0) * 0.0625 + offset.y;" +
      "float p10 = float((coord.y + 1.0) * 4.0 + coord.x) * 0.0625 + offset.y;" +
      "float p11 = float((coord.y + 1.0) * 4.0 + coord.x + 1.0) * 0.0625 + offset.y;" +
      "vec3 c00 = texture2D(inputImageTexture2, vec2(value, p00)).rgb;" +
      "vec3 c01 = texture2D(inputImageTexture2, vec2(value, p01)).rgb;" +
      "vec3 c10 = texture2D(inputImageTexture2, vec2(value, p10)).rgb;" +
      "vec3 c11 = texture2D(inputImageTexture2, vec2(value, p11)).rgb;" +
      "float c1 = ((c00.r - c00.g) / (c00.b - c00.g));" +
      "float c2 = ((c01.r - c01.g) / (c01.b - c01.g));" +
      "float c3 = ((c10.r - c10.g) / (c10.b - c10.g));" +
      "float c4 = ((c11.r - c11.g) / (c11.b - c11.g));" +
      "float c1_2 = mix(c1, c2, frac.x);" +
      "float c3_4 = mix(c3, c4, frac.x);" +
      "return mix(c1_2, c3_4, frac.y);" +
      "}" +
      "vec3 hsv_to_rgb(vec3 c) {" +
      "vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
      "vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
      "return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
      "}" +
      "void main() {" +
      "vec4 texel = texture2D(sourceImage, texCoord);" +
      "vec4 hsv = texel;" +
      "hsv.y = min(1.0, hsv.y * 1.2);" +
      "hsv.z = min(1.0, enhance(hsv.z) * 1.1);" +
      "gl_FragColor = vec4(hsv_to_rgb(mix(texel.xyz, hsv.xyz, intensity)), texel.w);" +
      "}";

  private static final String simpleVertexShaderCode =
    "attribute vec4 position;" +
      "attribute vec2 inputTexCoord;" +
      "varying vec2 texCoord;" +
      "void main() {" +
      "gl_Position = position;" +
      "texCoord = inputTexCoord;" +
      "}";

  private static final String simpleFragmentShaderCode =
    "varying highp vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "void main() {" +
      "gl_FragColor = texture2D(sourceImage, texCoord);" +
      "}";

  private static final String sharpenVertexShaderCode =
    "attribute vec4 position;" +
      "attribute vec2 inputTexCoord;" +
      "varying vec2 texCoord;" +

      "uniform highp float inputWidth;" +
      "uniform highp float inputHeight;" +
      "varying vec2 leftTexCoord;" +
      "varying vec2 rightTexCoord;" +
      "varying vec2 topTexCoord;" +
      "varying vec2 bottomTexCoord;" +

      "void main() {" +
      "gl_Position = position;" +
      "texCoord = inputTexCoord;" +
      "highp vec2 widthStep = vec2(1.0 / inputWidth, 0.0);" +
      "highp vec2 heightStep = vec2(0.0, 1.0 / inputHeight);" +
      "leftTexCoord = inputTexCoord - widthStep;" +
      "rightTexCoord = inputTexCoord + widthStep;" +
      "topTexCoord = inputTexCoord + heightStep;" +
      "bottomTexCoord = inputTexCoord - heightStep;" +
      "}";

  private static final String sharpenFragmentShaderCode =
    "precision highp float;" +
      "varying vec2 texCoord;" +
      "varying vec2 leftTexCoord;" +
      "varying vec2 rightTexCoord;" +
      "varying vec2 topTexCoord;" +
      "varying vec2 bottomTexCoord;" +
      "uniform sampler2D sourceImage;" +
      "uniform float sharpen;" +

      "void main() {" +
      "vec4 result = texture2D(sourceImage, texCoord);" +

      "vec3 leftTextureColor = texture2D(sourceImage, leftTexCoord).rgb;" +
      "vec3 rightTextureColor = texture2D(sourceImage, rightTexCoord).rgb;" +
      "vec3 topTextureColor = texture2D(sourceImage, topTexCoord).rgb;" +
      "vec3 bottomTextureColor = texture2D(sourceImage, bottomTexCoord).rgb;" +
      "result.rgb = result.rgb * (1.0 + 4.0 * sharpen) - (leftTextureColor + rightTextureColor + topTextureColor + bottomTextureColor) * sharpen;" +

      "gl_FragColor = result;" +
      "}";

  private static final String toolsFragmentShaderCode =
    "varying highp vec2 texCoord;" +
      "uniform sampler2D sourceImage;" +
      "uniform highp float width;" +
      "uniform highp float height;" +
      "uniform sampler2D curvesImage;" +
      "uniform lowp float skipTone;" +
      "uniform lowp float shadows;" +
      "const mediump vec3 hsLuminanceWeighting = vec3(0.3, 0.3, 0.3);" +
      "uniform lowp float highlights;" +
      "uniform lowp float contrast;" +
      "uniform lowp float fadeAmount;" +
      "const mediump vec3 satLuminanceWeighting = vec3(0.2126, 0.7152, 0.0722);" +
      "uniform lowp float saturation;" +
      "uniform lowp float shadowsTintIntensity;" +
      "uniform lowp float highlightsTintIntensity;" +
      "uniform lowp vec3 shadowsTintColor;" +
      "uniform lowp vec3 highlightsTintColor;" +
      "uniform lowp float exposure;" +
      "uniform lowp float warmth;" +
      "uniform lowp float grain;" +
      "const lowp float permTexUnit = 1.0 / 256.0;" +
      "const lowp float permTexUnitHalf = 0.5 / 256.0;" +
      "const lowp float grainsize = 2.3;" +
      "uniform lowp float vignette;" +
      "highp float getLuma(highp vec3 rgbP) {" +
      "return (0.299 * rgbP.r) + (0.587 * rgbP.g) + (0.114 * rgbP.b);" +
      "}" +
      "lowp vec3 rgbToHsv(lowp vec3 c) {" +
      "highp vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);" +
      "highp vec4 p = c.g < c.b ? vec4(c.bg, K.wz) : vec4(c.gb, K.xy);" +
      "highp vec4 q = c.r < p.x ? vec4(p.xyw, c.r) : vec4(c.r, p.yzx);" +
      "highp float d = q.x - min(q.w, q.y);" +
      "highp float e = 1.0e-10;" +
      "return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);" +
      "}" +
      "lowp vec3 hsvToRgb(lowp vec3 c) {" +
      "highp vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
      "highp vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
      "return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
      "}" +
      "highp vec3 rgbToHsl(highp vec3 color) {" +
      "highp vec3 hsl;" +
      "highp float fmin = min(min(color.r, color.g), color.b);" +
      "highp float fmax = max(max(color.r, color.g), color.b);" +
      "highp float delta = fmax - fmin;" +
      "hsl.z = (fmax + fmin) / 2.0;" +
      "if (delta == 0.0) {" +
      "hsl.x = 0.0;" +
      "hsl.y = 0.0;" +
      "} else {" +
      "if (hsl.z < 0.5) {" +
      "hsl.y = delta / (fmax + fmin);" +
      "} else {" +
      "hsl.y = delta / (2.0 - fmax - fmin);" +
      "}" +
      "highp float deltaR = (((fmax - color.r) / 6.0) + (delta / 2.0)) / delta;" +
      "highp float deltaG = (((fmax - color.g) / 6.0) + (delta / 2.0)) / delta;" +
      "highp float deltaB = (((fmax - color.b) / 6.0) + (delta / 2.0)) / delta;" +
      "if (color.r == fmax) {" +
      "hsl.x = deltaB - deltaG;" +
      "} else if (color.g == fmax) {" +
      "hsl.x = (1.0 / 3.0) + deltaR - deltaB;" +
      "} else if (color.b == fmax) {" +
      "hsl.x = (2.0 / 3.0) + deltaG - deltaR;" +
      "}" +
      "if (hsl.x < 0.0) {" +
      "hsl.x += 1.0;" +
      "} else if (hsl.x > 1.0) {" +
      "hsl.x -= 1.0;" +
      "}" +
      "}" +
      "return hsl;" +
      "}" +
      "highp float hueToRgb(highp float f1, highp float f2, highp float hue) {" +
      "if (hue < 0.0) {" +
      "hue += 1.0;" +
      "} else if (hue > 1.0) {" +
      "hue -= 1.0;" +
      "}" +
      "highp float res;" +
      "if ((6.0 * hue) < 1.0) {" +
      "res = f1 + (f2 - f1) * 6.0 * hue;" +
      "} else if ((2.0 * hue) < 1.0) {" +
      "res = f2;" +
      "} else if ((3.0 * hue) < 2.0) {" +
      "res = f1 + (f2 - f1) * ((2.0 / 3.0) - hue) * 6.0;" +
      "} else {" +
      "res = f1;" +
      "} return res;" +
      "}" +
      "highp vec3 hslToRgb(highp vec3 hsl) {" +
      "if (hsl.y == 0.0) {" +
      "return vec3(hsl.z);" +
      "} else {" +
      "highp float f2;" +
      "if (hsl.z < 0.5) {" +
      "f2 = hsl.z * (1.0 + hsl.y);" +
      "} else {" +
      "f2 = (hsl.z + hsl.y) - (hsl.y * hsl.z);" +
      "}" +
      "highp float f1 = 2.0 * hsl.z - f2;" +
      "return vec3(hueToRgb(f1, f2, hsl.x + (1.0/3.0)), hueToRgb(f1, f2, hsl.x), hueToRgb(f1, f2, hsl.x - (1.0/3.0)));" +
      "}" +
      "}" +
      "highp vec3 rgbToYuv(highp vec3 inP) {" +
      "highp float luma = getLuma(inP);" +
      "return vec3(luma, (1.0 / 1.772) * (inP.b - luma), (1.0 / 1.402) * (inP.r - luma));" +
      "}" +
      "lowp vec3 yuvToRgb(highp vec3 inP) {" +
      "return vec3(1.402 * inP.b + inP.r, (inP.r - (0.299 * 1.402 / 0.587) * inP.b - (0.114 * 1.772 / 0.587) * inP.g), 1.772 * inP.g + inP.r);" +
      "}" +
      "lowp float easeInOutSigmoid(lowp float value, lowp float strength) {" +
      "if (value > 0.5) {" +
      "return 1.0 - pow(2.0 - 2.0 * value, 1.0 / (1.0 - strength)) * 0.5;" +
      "} else {" +
      "return pow(2.0 * value, 1.0 / (1.0 - strength)) * 0.5;" +
      "}" +
      "}" +
      "lowp vec3 applyLuminanceCurve(lowp vec3 pixel) {" +
      "highp float index = floor(clamp(pixel.z / (1.0 / 200.0), 0.0, 199.0));" +
      "pixel.y = mix(0.0, pixel.y, smoothstep(0.0, 0.1, pixel.z) * (1.0 - smoothstep(0.8, 1.0, pixel.z)));" +
      "pixel.z = texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).a;" +
      "return pixel;" +
      "}" +
      "lowp vec3 applyRGBCurve(lowp vec3 pixel) {" +
      "highp float index = floor(clamp(pixel.r / (1.0 / 200.0), 0.0, 199.0));" +
      "pixel.r = texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).r;" +
      "index = floor(clamp(pixel.g / (1.0 / 200.0), 0.0, 199.0));" +
      "pixel.g = clamp(texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).g, 0.0, 1.0);" +
      "index = floor(clamp(pixel.b / (1.0 / 200.0), 0.0, 199.0));" +
      "pixel.b = clamp(texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).b, 0.0, 1.0);" +
      "return pixel;" +
      "}" +
      "highp vec3 fadeAdjust(highp vec3 color, highp float fadeVal) {" +
      "return (color * (1.0 - fadeVal)) + ((color + (vec3(-0.9772) * pow(vec3(color), vec3(3.0)) + vec3(1.708) * pow(vec3(color), vec3(2.0)) + vec3(-0.1603) * vec3(color) + vec3(0.2878) - color * vec3(0.9))) * fadeVal);" +
      "}" +
      "lowp vec3 tintRaiseShadowsCurve(lowp vec3 color) {" +
      "return vec3(-0.003671) * pow(color, vec3(3.0)) + vec3(0.3842) * pow(color, vec3(2.0)) + vec3(0.3764) * color + vec3(0.2515);" +
      "}" +
      "lowp vec3 tintShadows(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
      "return clamp(mix(texel, mix(texel, tintRaiseShadowsCurve(texel), tintColor), tintAmount), 0.0, 1.0);" +
      "} " +
      "lowp vec3 tintHighlights(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
      "return clamp(mix(texel, mix(texel, vec3(1.0) - tintRaiseShadowsCurve(vec3(1.0) - texel), (vec3(1.0) - tintColor)), tintAmount), 0.0, 1.0);" +
      "}" +
      "highp vec4 rnm(in highp vec2 tc) {" +
      "highp float noise = sin(dot(tc, vec2(12.9898, 78.233))) * 43758.5453;" +
      "return vec4(fract(noise), fract(noise * 1.2154), fract(noise * 1.3453), fract(noise * 1.3647)) * 2.0 - 1.0;" +
      "}" +
      "highp float fade(in highp float t) {" +
      "return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);" +
      "}" +
      "highp float pnoise3D(in highp vec3 p) {" +
      "highp vec3 pi = permTexUnit * floor(p) + permTexUnitHalf;" +
      "highp vec3 pf = fract(p);" +
      "highp float perm = rnm(pi.xy).a;" +
      "highp float n000 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf);" +
      "highp float n001 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(0.0, 0.0, 1.0));" +
      "perm = rnm(pi.xy + vec2(0.0, permTexUnit)).a;" +
      "highp float n010 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(0.0, 1.0, 0.0));" +
      "highp float n011 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(0.0, 1.0, 1.0));" +
      "perm = rnm(pi.xy + vec2(permTexUnit, 0.0)).a;" +
      "highp float n100 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(1.0, 0.0, 0.0));" +
      "highp float n101 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(1.0, 0.0, 1.0));" +
      "perm = rnm(pi.xy + vec2(permTexUnit, permTexUnit)).a;" +
      "highp float n110 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(1.0, 1.0, 0.0));" +
      "highp float n111 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(1.0, 1.0, 1.0));" +
      "highp vec4 n_x = mix(vec4(n000, n001, n010, n011), vec4(n100, n101, n110, n111), fade(pf.x));" +
      "highp vec2 n_xy = mix(n_x.xy, n_x.zw, fade(pf.y));" +
      "return mix(n_xy.x, n_xy.y, fade(pf.z));" +
      "}" +
      "lowp vec2 coordRot(in lowp vec2 tc, in lowp float angle) {" +
      "return vec2(((tc.x * 2.0 - 1.0) * cos(angle) - (tc.y * 2.0 - 1.0) * sin(angle)) * 0.5 + 0.5, ((tc.y * 2.0 - 1.0) * cos(angle) + (tc.x * 2.0 - 1.0) * sin(angle)) * 0.5 + 0.5);" +
      "}" +
      "void main() {" +
      "lowp vec4 source = texture2D(sourceImage, texCoord);" +
      "lowp vec4 result = source;" +
      "const lowp float toolEpsilon = 0.005;" +
      "if (skipTone < toolEpsilon) {" +
      "result = vec4(applyRGBCurve(hslToRgb(applyLuminanceCurve(rgbToHsl(result.rgb)))), result.a);" +
      "}" +
      "mediump float hsLuminance = dot(result.rgb, hsLuminanceWeighting);" +
      "mediump float shadow = clamp((pow(hsLuminance, 1.0 / shadows) + (-0.76) * pow(hsLuminance, 2.0 / shadows)) - hsLuminance, 0.0, 1.0);" +
      "mediump float highlight = clamp((1.0 - (pow(1.0 - hsLuminance, 1.0 / (2.0 - highlights)) + (-0.8) * pow(1.0 - hsLuminance, 2.0 / (2.0 - highlights)))) - hsLuminance, -1.0, 0.0);" +
      "lowp vec3 hsresult = vec3(0.0, 0.0, 0.0) + ((hsLuminance + shadow + highlight) - 0.0) * ((result.rgb - vec3(0.0, 0.0, 0.0)) / (hsLuminance - 0.0));" +
      "mediump float contrastedLuminance = ((hsLuminance - 0.5) * 1.5) + 0.5;" +
      "mediump float whiteInterp = contrastedLuminance * contrastedLuminance * contrastedLuminance;" +
      "mediump float whiteTarget = clamp(highlights, 1.0, 2.0) - 1.0;" +
      "hsresult = mix(hsresult, vec3(1.0), whiteInterp * whiteTarget);" +
      "mediump float invContrastedLuminance = 1.0 - contrastedLuminance;" +
      "mediump float blackInterp = invContrastedLuminance * invContrastedLuminance * invContrastedLuminance;" +
      "mediump float blackTarget = 1.0 - clamp(shadows, 0.0, 1.0);" +
      "hsresult = mix(hsresult, vec3(0.0), blackInterp * blackTarget);" +
      "result = vec4(hsresult.rgb, result.a);" +
      "result = vec4(clamp(((result.rgb - vec3(0.5)) * contrast + vec3(0.5)), 0.0, 1.0), result.a);" +
      "if (abs(fadeAmount) > toolEpsilon) {" +
      "result.rgb = fadeAdjust(result.rgb, fadeAmount);" +
      "}" +
      "lowp float satLuminance = dot(result.rgb, satLuminanceWeighting);" +
      "lowp vec3 greyScaleColor = vec3(satLuminance);" +
      "result = vec4(clamp(mix(greyScaleColor, result.rgb, saturation), 0.0, 1.0), result.a);" +
      "if (abs(shadowsTintIntensity) > toolEpsilon) {" +
      "result.rgb = tintShadows(result.rgb, shadowsTintColor, shadowsTintIntensity * 2.0);" +
      "}" +
      "if (abs(highlightsTintIntensity) > toolEpsilon) {" +
      "result.rgb = tintHighlights(result.rgb, highlightsTintColor, highlightsTintIntensity * 2.0);" +
      "}" +
      "if (abs(exposure) > toolEpsilon) {" +
      "mediump float mag = exposure * 1.045;" +
      "mediump float exppower = 1.0 + abs(mag);" +
      "if (mag < 0.0) {" +
      "exppower = 1.0 / exppower;" +
      "}" +
      "result.r = 1.0 - pow((1.0 - result.r), exppower);" +
      "result.g = 1.0 - pow((1.0 - result.g), exppower);" +
      "result.b = 1.0 - pow((1.0 - result.b), exppower);" +
      "}" +
      "if (abs(warmth) > toolEpsilon) {" +
      "highp vec3 yuvVec;" +
      "if (warmth > 0.0 ) {" +
      "yuvVec = vec3(0.1765, -0.1255, 0.0902);" +
      "} else {" +
      "yuvVec = -vec3(0.0588, 0.1569, -0.1255);" +
      "}" +
      "highp vec3 yuvColor = rgbToYuv(result.rgb);" +
      "highp float luma = yuvColor.r;" +
      "highp float curveScale = sin(luma * 3.14159);" +
      "yuvColor += 0.375 * warmth * curveScale * yuvVec;" +
      "result.rgb = yuvToRgb(yuvColor);" +
      "}" +
      "if (abs(grain) > toolEpsilon) {" +
      "highp vec3 rotOffset = vec3(1.425, 3.892, 5.835);" +
      "highp vec2 rotCoordsR = coordRot(texCoord, rotOffset.x);" +
      "highp vec3 noise = vec3(pnoise3D(vec3(rotCoordsR * vec2(width / grainsize, height / grainsize),0.0)));" +
      "lowp vec3 lumcoeff = vec3(0.299,0.587,0.114);" +
      "lowp float luminance = dot(result.rgb, lumcoeff);" +
      "lowp float lum = smoothstep(0.2, 0.0, luminance);" +
      "lum += luminance;" +
      "noise = mix(noise,vec3(0.0),pow(lum,4.0));" +
      "result.rgb = result.rgb + noise * grain;" +
      "}" +
      "if (abs(vignette) > toolEpsilon) {" +
      "const lowp float midpoint = 0.7;" +
      "const lowp float fuzziness = 0.62;" +
      "lowp float radDist = length(texCoord - 0.5) / sqrt(0.5);" +
      "lowp float mag = easeInOutSigmoid(radDist * midpoint, fuzziness) * vignette * 0.645;" +
      "result.rgb = mix(pow(result.rgb, vec3(1.0 / (1.0 - mag))), vec3(0.0), mag * mag);" +
      "}" +
      "gl_FragColor = result;" +
      "}";


  // EGL shit
  private GL gl;
  private EGL10 egl10;
  private EGLDisplay eglDisplay;
  private EGLConfig eglConfig;
  private EGLContext eglContext;
  private EGLSurface eglSurface;
  
  
  // UI-related

  private long lastRenderCallTime;
  private SurfaceTexture surfaceTexture;
  private @Nullable Bitmap currentBitmap;
  
  
  // Programs

  private int rgbToHsvShaderProgram;
  private int rgbToHsvPositionHandle;
  private int rgbToHsvInputTexCoordHandle;
  private int rgbToHsvSourceImageHandle;

  private int enhanceShaderProgram;
  private int enhancePositionHandle;
  private int enhanceInputTexCoordHandle;
  private int enhanceSourceImageHandle;
  private int enhanceIntensityHandle;
  private int enhanceInputImageTexture2Handle;

  private int toolsShaderProgram;
  private int positionHandle;
  private int inputTexCoordHandle; //"varying vec2 texCoord;" +
  private int sourceImageHandle; //"uniform sampler2D sourceImage;" +
  private int shadowsHandle; //"uniform float shadows;" +
  private int highlightsHandle; //"uniform float highlights;" +
  private int exposureHandle; //"uniform float exposure;" +
  private int contrastHandle; //"uniform float contrast;" +
  private int saturationHandle; //"uniform float saturation;" +
  private int warmthHandle; //"uniform float warmth;" +
  private int vignetteHandle; //"uniform float vignette;" +
  private int grainHandle; //"uniform float grain;" +
  private int widthHandle; //"uniform float width;" +
  private int heightHandle; //"uniform float height;" +

  private int curvesImageHandle; //"uniform sampler2D curvesImage;" +
  private int skipToneHandle; //"uniform lowp float skipTone;" +
  private int fadeAmountHandle; //"uniform lowp float fadeAmount;" +
  private int shadowsTintIntensityHandle; //"uniform lowp float shadowsTintIntensity;" +
  private int highlightsTintIntensityHandle; //"uniform lowp float highlightsTintIntensity;" +
  private int shadowsTintColorHandle; //"uniform lowp vec3 shadowsTintColor;" +
  private int highlightsTintColorHandle; //"uniform lowp vec3 highlightsTintColor;" +

  private int blurShaderProgram;
  private int blurPositionHandle;
  private int blurInputTexCoordHandle;
  private int blurSourceImageHandle;
  private int blurWidthHandle;
  private int blurHeightHandle;

  private int linearBlurShaderProgram;
  private int linearBlurPositionHandle;
  private int linearBlurInputTexCoordHandle;
  private int linearBlurSourceImageHandle;
  private int linearBlurSourceImage2Handle;
  private int linearBlurExcludeSizeHandle;
  private int linearBlurExcludePointHandle;
  private int linearBlurExcludeBlurSizeHandle;
  private int linearBlurAngleHandle;
  private int linearBlurAspectRatioHandle;

  private int radialBlurShaderProgram;
  private int radialBlurPositionHandle;
  private int radialBlurInputTexCoordHandle;
  private int radialBlurSourceImageHandle;
  private int radialBlurSourceImage2Handle;
  private int radialBlurExcludeSizeHandle;
  private int radialBlurExcludePointHandle;
  private int radialBlurExcludeBlurSizeHandle;
  private int radialBlurAspectRatioHandle;

  private int sharpenShaderProgram;
  private int sharpenHandle;
  private int sharpenWidthHandle;
  private int sharpenHeightHandle;
  private int sharpenPositionHandle;
  private int sharpenInputTexCoordHandle;
  private int sharpenSourceImageHandle;

  private int simpleShaderProgram;
  private int simplePositionHandle;
  private int simpleInputTexCoordHandle;
  private int simpleSourceImageHandle;

  private int[] enhanceTextures = new int[2];
  private int[] renderTexture = new int[3];
  private int[] renderFrameBuffer = new int[3];
  private int[] curveTextures = new int[1];
  private boolean hsvGenerated;
  private boolean showOriginal;
  private int orientation;
  private int renderBufferWidth;
  private int renderBufferHeight;
  private volatile int surfaceWidth;
  private volatile int surfaceHeight;

  private FloatBuffer vertexBuffer;
  private FloatBuffer textureBuffer;
  private FloatBuffer vertexInvertBuffer;

  private final static int PGPhotoEnhanceHistogramBins = 256;
  private final static int PGPhotoEnhanceSegments = 4;

  private boolean inited;

  private FiltersState currentState;

  private final BaseThread queue;

  private static final int ACTION_DESTROY = 0;
  private static final int ACTION_INIT = 1;
  private static final int ACTION_RENDER = 2;
  private static final int ACTION_RESUME = 3;
  private static final int ACTION_PAUSE = 4;
  private static final int ACTION_GET_BITMAP = 5;

  public EGLEditorContext (SurfaceTexture surfaceTexture, @Nullable Bitmap currentBitmap, FiltersState state, int width, int height) {
    this.queue = new BaseThread("EGLEditorQueue") {
      @Override
      protected void process (Message msg) {
        switch (msg.what) {
          case ACTION_DESTROY: {
            finish();
            quitLooper(true);
            break;
          }
          case ACTION_INIT: {
            inited = initGL();
            break;
          }
          case ACTION_RENDER: {
            requestRenderInternal(msg.arg1 == 1, msg.arg2 == 1);
            break;
          }
          case ACTION_PAUSE: {
            pauseInternal();
            break;
          }
          case ACTION_RESUME: {
            Object[] data = (Object[]) msg.obj;
            resumeWithDataInternal((Bitmap) data[0], (FiltersState) data[1]);
            break;
          }
          case ACTION_GET_BITMAP: {
            getBitmapInternal((BitmapCallback) msg.obj);
            break;
          }
        }
      }
    };

    this.currentBitmap = currentBitmap;
    this.surfaceTexture = surfaceTexture;
    this.surfaceWidth = width;
    this.surfaceHeight = height;

    this.currentState = state;

    this.queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_INIT), 0);
  }

  // Public interaction

  public void resumeWithData (Bitmap newBitmap, FiltersState newState) {
    queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_RESUME, new Object[] {newBitmap, newState}), 0);
  }

  public void destroy () {
    queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_DESTROY), 0);
  }

  private boolean isPaused;

  public void pause () {
    queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_PAUSE), 0);
  }

  public void setSurfaceTextureSize (int width, int height) {
    this.surfaceWidth = width;
    this.surfaceHeight = height;
  }

  public void requestRender (boolean updateBlur) {
    requestRender(updateBlur, false);
  }

  public void requestRender (boolean updateBlur, boolean force) {
    queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_RENDER, updateBlur ? 1 : 0, force ? 1 : 0), 0);
  }

  public void getBitmap (BitmapCallback callback) {
    queue.sendMessage(Message.obtain(queue.getHandler(), ACTION_GET_BITMAP, callback), 0);
  }

  // Internal

  private boolean needUpdateBlurTexture;

  private void requestRenderInternal (boolean updateBlur, boolean force) {
    if (!needUpdateBlurTexture) {
      needUpdateBlurTexture = updateBlur;
    }
    long now = System.currentTimeMillis();
    if (force || now - lastRenderCallTime > 30) {
      lastRenderCallTime = now;
      render();
    }
  }

  private void resumeWithDataInternal (Bitmap bitmap, FiltersState newState) {
    this.currentBitmap = bitmap;
    this.currentState = newState;
    loadTexture(bitmap);

    isPaused = false;
    requestRenderInternal(true, true);
  }

  private void pauseInternal () {
    if (!isPaused) {
      // TODO unload texture maybe?
      isPaused = true;
    }
  }

  private void getBitmapInternal (final BitmapCallback callback) {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[blured ? 0 : 1], 0);
    GLES20.glClear(0);
    final Bitmap bitmap = getRenderBufferBitmap();
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glClear(0);
    UI.post(() -> callback.onBitmapObtained(bitmap));
  }

  private Bitmap getRenderBufferBitmap() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(renderBufferWidth * renderBufferHeight * 4);
    GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
    Bitmap bitmap = Bitmap.createBitmap(renderBufferWidth, renderBufferHeight, Bitmap.Config.ARGB_8888);
    bitmap.copyPixelsFromBuffer(buffer);
    return bitmap;
  }

  private boolean blured;

  private void render () {
    if (!inited || isPaused) {
      return;
    }

    if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
      if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        Log.e("eglMakeCurrent failed %s", U.getEGLErrorString(egl10.eglGetError()));
        return;
      }
    }

    GLES20.glViewport(0, 0, renderBufferWidth, renderBufferHeight);
    drawEnhancePass();
    drawSharpenPass();
    drawCustomParamsPass();
    blured = drawBlurPass();

    //onscreen draw
    GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glClear(0);

    GLES20.glUseProgram(simpleShaderProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[blured ? 0 : 1]);
    GLES20.glUniform1i(simpleSourceImageHandle, 0);
    GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
    GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
    GLES20.glEnableVertexAttribArray(simplePositionHandle);
    GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    egl10.eglSwapBuffers(eglDisplay, eglSurface);
  }

  // Called on queue thread

  private int loadShader(int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
    if (compileStatus[0] == 0) {
      Log.e(GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }
    return shader;
  }

  private boolean initGL () {
    egl10 = (EGL10) EGLContext.getEGL();

    eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
      Log.e("eglGetDisplay failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] version = new int[2];
    if (!egl10.eglInitialize(eglDisplay, version)) {
      Log.e("eglInitialize failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] configsCount = new int[1];
    EGLConfig[] configs = new EGLConfig[1];
    int[] configSpec = new int[] {
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_RED_SIZE, 8,
      EGL10.EGL_GREEN_SIZE, 8,
      EGL10.EGL_BLUE_SIZE, 8,
      EGL10.EGL_ALPHA_SIZE, 8,
      EGL10.EGL_DEPTH_SIZE, 0,
      EGL10.EGL_STENCIL_SIZE, 0,
      EGL10.EGL_NONE
    };
    if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
      Log.e("eglChooseConfig failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    } else if (configsCount[0] > 0) {
      eglConfig = configs[0];
    } else {
      Log.e("eglConfig not initialized");
      finish();
      return false;
    }

    int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
    eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    if (eglContext == null) {
      Log.e("eglCreateContext failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    if (surfaceTexture != null) {
      eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
    } else {
      finish();
      return false;
    }

    if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
      Log.e("createWindowSurface failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      Log.e("eglMakeCurrent failed: %s", U.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    gl = eglContext.getGL();


    float[] squareCoordinates = {
      -1.0f, 1.0f,
      1.0f, 1.0f,
      -1.0f, -1.0f,
      1.0f, -1.0f};

    ByteBuffer bb = ByteBuffer.allocateDirect(squareCoordinates.length * 4);
    bb.order(ByteOrder.nativeOrder());
    vertexBuffer = bb.asFloatBuffer();
    vertexBuffer.put(squareCoordinates);
    vertexBuffer.position(0);

    float[] squareCoordinates2 = {
      -1.0f, -1.0f,
      1.0f, -1.0f,
      -1.0f, 1.0f,
      1.0f, 1.0f};

    bb = ByteBuffer.allocateDirect(squareCoordinates2.length * 4);
    bb.order(ByteOrder.nativeOrder());
    vertexInvertBuffer = bb.asFloatBuffer();
    vertexInvertBuffer.put(squareCoordinates2);
    vertexInvertBuffer.position(0);

    float[] textureCoordinates = {
      0.0f, 0.0f,
      1.0f, 0.0f,
      0.0f, 1.0f,
      1.0f, 1.0f,
    };

    bb = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
    bb.order(ByteOrder.nativeOrder());
    textureBuffer = bb.asFloatBuffer();
    textureBuffer.put(textureCoordinates);
    textureBuffer.position(0);

    GLES20.glGenTextures(1, curveTextures, 0);
    GLES20.glGenTextures(2, enhanceTextures, 0);

    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, toolsFragmentShaderCode);

    if (vertexShader != 0 && fragmentShader != 0) {
      toolsShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(toolsShaderProgram, vertexShader);
      GLES20.glAttachShader(toolsShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(toolsShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(toolsShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(toolsShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(toolsShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
                    /*String infoLog = GLES20.glGetProgramInfoLog(toolsShaderProgram);
                    Logger.e("link error = " + infoLog);*/
        GLES20.glDeleteProgram(toolsShaderProgram);
        toolsShaderProgram = 0;
      } else {
        positionHandle = GLES20.glGetAttribLocation(toolsShaderProgram, "position");
        inputTexCoordHandle = GLES20.glGetAttribLocation(toolsShaderProgram, "inputTexCoord");
        sourceImageHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "sourceImage");
        shadowsHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadows");
        highlightsHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlights");
        exposureHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "exposure");
        contrastHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "contrast");
        saturationHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "saturation");
        warmthHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "warmth");
        vignetteHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "vignette");
        grainHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "grain");
        widthHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "width");
        heightHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "height");
        curvesImageHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "curvesImage");
        skipToneHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "skipTone");
        fadeAmountHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "fadeAmount");
        shadowsTintIntensityHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadowsTintIntensity");
        highlightsTintIntensityHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlightsTintIntensity");
        shadowsTintColorHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadowsTintColor");
        highlightsTintColorHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlightsTintColor");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, sharpenVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, sharpenFragmentShaderCode);

    if (vertexShader != 0 && fragmentShader != 0) {
      sharpenShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(sharpenShaderProgram, vertexShader);
      GLES20.glAttachShader(sharpenShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(sharpenShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(sharpenShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(sharpenShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(sharpenShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(sharpenShaderProgram);
        sharpenShaderProgram = 0;
      } else {
        sharpenPositionHandle = GLES20.glGetAttribLocation(sharpenShaderProgram, "position");
        sharpenInputTexCoordHandle = GLES20.glGetAttribLocation(sharpenShaderProgram, "inputTexCoord");
        sharpenSourceImageHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "sourceImage");
        sharpenWidthHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "inputWidth");
        sharpenHeightHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "inputHeight");
        sharpenHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "sharpen");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, blurVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, blurFragmentShaderCode);

    if (vertexShader != 0 && fragmentShader != 0) {
      blurShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(blurShaderProgram, vertexShader);
      GLES20.glAttachShader(blurShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(blurShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(blurShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(blurShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(blurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(blurShaderProgram);
        blurShaderProgram = 0;
      } else {
        blurPositionHandle = GLES20.glGetAttribLocation(blurShaderProgram, "position");
        blurInputTexCoordHandle = GLES20.glGetAttribLocation(blurShaderProgram, "inputTexCoord");
        blurSourceImageHandle = GLES20.glGetUniformLocation(blurShaderProgram, "sourceImage");
        blurWidthHandle = GLES20.glGetUniformLocation(blurShaderProgram, "texelWidthOffset");
        blurHeightHandle = GLES20.glGetUniformLocation(blurShaderProgram, "texelHeightOffset");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, linearBlurFragmentShaderCode);

    if (vertexShader != 0 && fragmentShader != 0) {
      linearBlurShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(linearBlurShaderProgram, vertexShader);
      GLES20.glAttachShader(linearBlurShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(linearBlurShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(linearBlurShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(linearBlurShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(linearBlurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(linearBlurShaderProgram);
        linearBlurShaderProgram = 0;
      } else {
        linearBlurPositionHandle = GLES20.glGetAttribLocation(linearBlurShaderProgram, "position");
        linearBlurInputTexCoordHandle = GLES20.glGetAttribLocation(linearBlurShaderProgram, "inputTexCoord");
        linearBlurSourceImageHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "sourceImage");
        linearBlurSourceImage2Handle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "inputImageTexture2");
        linearBlurExcludeSizeHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludeSize");
        linearBlurExcludePointHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludePoint");
        linearBlurExcludeBlurSizeHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludeBlurSize");
        linearBlurAngleHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "angle");
        linearBlurAspectRatioHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "aspectRatio");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, radialBlurFragmentShaderCode);

    if (vertexShader != 0 && fragmentShader != 0) {
      radialBlurShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(radialBlurShaderProgram, vertexShader);
      GLES20.glAttachShader(radialBlurShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(radialBlurShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(radialBlurShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(radialBlurShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(radialBlurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(radialBlurShaderProgram);
        radialBlurShaderProgram = 0;
      } else {
        radialBlurPositionHandle = GLES20.glGetAttribLocation(radialBlurShaderProgram, "position");
        radialBlurInputTexCoordHandle = GLES20.glGetAttribLocation(radialBlurShaderProgram, "inputTexCoord");
        radialBlurSourceImageHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "sourceImage");
        radialBlurSourceImage2Handle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "inputImageTexture2");
        radialBlurExcludeSizeHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludeSize");
        radialBlurExcludePointHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludePoint");
        radialBlurExcludeBlurSizeHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludeBlurSize");
        radialBlurAspectRatioHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "aspectRatio");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, rgbToHsvFragmentShaderCode);
    if (vertexShader != 0 && fragmentShader != 0) {
      rgbToHsvShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(rgbToHsvShaderProgram, vertexShader);
      GLES20.glAttachShader(rgbToHsvShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(rgbToHsvShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(rgbToHsvShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(rgbToHsvShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(rgbToHsvShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(rgbToHsvShaderProgram);
        rgbToHsvShaderProgram = 0;
      } else {
        rgbToHsvPositionHandle = GLES20.glGetAttribLocation(rgbToHsvShaderProgram, "position");
        rgbToHsvInputTexCoordHandle = GLES20.glGetAttribLocation(rgbToHsvShaderProgram, "inputTexCoord");
        rgbToHsvSourceImageHandle = GLES20.glGetUniformLocation(rgbToHsvShaderProgram, "sourceImage");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, enhanceFragmentShaderCode);
    if (vertexShader != 0 && fragmentShader != 0) {
      enhanceShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(enhanceShaderProgram, vertexShader);
      GLES20.glAttachShader(enhanceShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(enhanceShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(enhanceShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(enhanceShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(enhanceShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(enhanceShaderProgram);
        enhanceShaderProgram = 0;
      } else {
        enhancePositionHandle = GLES20.glGetAttribLocation(enhanceShaderProgram, "position");
        enhanceInputTexCoordHandle = GLES20.glGetAttribLocation(enhanceShaderProgram, "inputTexCoord");
        enhanceSourceImageHandle = GLES20.glGetUniformLocation(enhanceShaderProgram, "sourceImage");
        enhanceIntensityHandle = GLES20.glGetUniformLocation(enhanceShaderProgram, "intensity");
        enhanceInputImageTexture2Handle = GLES20.glGetUniformLocation(enhanceShaderProgram, "inputImageTexture2");
      }
    } else {
      finish();
      return false;
    }

    vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
    fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, simpleFragmentShaderCode);
    if (vertexShader != 0 && fragmentShader != 0) {
      simpleShaderProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(simpleShaderProgram, vertexShader);
      GLES20.glAttachShader(simpleShaderProgram, fragmentShader);
      GLES20.glBindAttribLocation(simpleShaderProgram, 0, "position");
      GLES20.glBindAttribLocation(simpleShaderProgram, 1, "inputTexCoord");

      GLES20.glLinkProgram(simpleShaderProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(simpleShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(simpleShaderProgram);
        simpleShaderProgram = 0;
      } else {
        simplePositionHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "position");
        simpleInputTexCoordHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "inputTexCoord");
        simpleSourceImageHandle = GLES20.glGetUniformLocation(simpleShaderProgram, "sourceImage");
      }
    } else {
      finish();
      return false;
    }

    if (currentBitmap != null) {
      loadTexture(currentBitmap);
    }

    return true;
  }

  private void finish () {
    if (eglSurface != null) {
      egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
      egl10.eglDestroySurface(eglDisplay, eglSurface);
      eglSurface = null;
    }
    if (eglContext != null) {
      egl10.eglDestroyContext(eglDisplay, eglContext);
      eglContext = null;
    }
    if (eglDisplay != null) {
      egl10.eglTerminate(eglDisplay);
      eglDisplay = null;
    }
  }

  private void loadTexture (Bitmap bitmap) {
    renderBufferWidth = bitmap.getWidth();
    renderBufferHeight = bitmap.getHeight();

    GLES20.glGenFramebuffers(3, renderFrameBuffer, 0);
    GLES20.glGenTextures(3, renderTexture, 0);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, renderTexture[1]);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, currentBitmap, 0);

    // TODO play with to support RGB_565
    // GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, byteBuffer);
    // GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_SHORT_4_4_4_4, byteBuffer);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[2]);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
  }

  // Getters

  private float getFactor (int key) {
    return currentState.getFactor(key);
  }

  private float getValue (int key) {
    return currentState.getValue(key);
  }

  private float getEnhanceValue () {
    return getFactor(FiltersState.KEY_ENHANCE);
  }

  private float getExposureValue () {
    return getFactor(FiltersState.KEY_EXPOSURE);
  }

  private float getWarmthValue () {
    return getFactor(FiltersState.KEY_WARMTH);
  }

  private float getVignetteValue () {
    return getFactor(FiltersState.KEY_VIGNETTE);
  }

  private float getSharpenValue () {
    return .11f + getFactor(FiltersState.KEY_SHARPEN) * .6f;
  }

  private float getGrainValue () {
    return getFactor(FiltersState.KEY_GRAIN) * .04f;
  }

  private float getShadowsValue () {
    return (getValue(FiltersState.KEY_SHADOWS) * .55f + 100f) / 100f;
  }

  private float getHighlightsValue () {
    return (getValue(FiltersState.KEY_HIGHLIGHTS) * 0.75f + 100.0f) / 100.0f;
  }

  private float getContrastValue () {
    return 1f + getFactor(FiltersState.KEY_CONTRAST) * .3f;
  }

  private float getSaturationValue () {
    float saturation = getFactor(FiltersState.KEY_SATURATION);
    if (saturation > 0) {
      saturation *= 1.05f;
    }
    return saturation + 1f;
  }

  private float getFadeValue () {
    return currentState.getFactor(FiltersState.KEY_FADE);
  }

  private float getTintHighlightsIntensityValue () {
    int colorId = currentState.getValue(FiltersState.KEY_HIGHLIGHTS_COLOR_ID);
    return colorId == 0 ? 0f : .5f;
  }

  private float getTintShadowsIntensityValue () {
    int colorId = currentState.getValue(FiltersState.KEY_SHADOWS_COLOR_ID);
    return colorId == 0 ? 0f : .5f;
  }

  // Curves, reserved for future

  private boolean shouldSkipCurvesPass () {
    return true; // TODO (?)
  }

  private void fillCurvesBuffer () {}

  private ByteBuffer getCurveBuffer () {
    return null;
  }

  // Drawing

  private void drawEnhancePass() {
    if (!hsvGenerated) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
      GLES20.glClear(0);

      GLES20.glUseProgram(rgbToHsvShaderProgram);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
      GLES20.glUniform1i(rgbToHsvSourceImageHandle, 0);
      GLES20.glEnableVertexAttribArray(rgbToHsvInputTexCoordHandle);
      GLES20.glVertexAttribPointer(rgbToHsvInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
      GLES20.glEnableVertexAttribArray(rgbToHsvPositionHandle);
      GLES20.glVertexAttribPointer(rgbToHsvPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

      ByteBuffer hsvBuffer = ByteBuffer.allocateDirect(renderBufferWidth * renderBufferHeight * 4);
      GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, hsvBuffer);

      GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[0]);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, hsvBuffer);

      ByteBuffer buffer = null;
      try {
        buffer = ByteBuffer.allocateDirect(PGPhotoEnhanceSegments * PGPhotoEnhanceSegments * PGPhotoEnhanceHistogramBins * 4);
        N.calcCDT(hsvBuffer, renderBufferWidth, renderBufferHeight, buffer);
      } catch (Throwable t) {
        Log.e("Cannot allocate buffer for CDT", t);
      }

      GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[1]);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 16, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

      hsvGenerated = true;
    }

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
    GLES20.glClear(0);

    GLES20.glUseProgram(enhanceShaderProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[0]);
    GLES20.glUniform1i(enhanceSourceImageHandle, 0);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[1]);
    GLES20.glUniform1i(enhanceInputImageTexture2Handle, 1);
    if (showOriginal) {
      GLES20.glUniform1f(enhanceIntensityHandle, 0);
    } else {
      GLES20.glUniform1f(enhanceIntensityHandle, getEnhanceValue());
    }

    GLES20.glEnableVertexAttribArray(enhanceInputTexCoordHandle);
    GLES20.glVertexAttribPointer(enhanceInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
    GLES20.glEnableVertexAttribArray(enhancePositionHandle);
    GLES20.glVertexAttribPointer(enhancePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  }

  private void drawSharpenPass() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
    GLES20.glClear(0);

    GLES20.glUseProgram(sharpenShaderProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
    GLES20.glUniform1i(sharpenSourceImageHandle, 0);
    if (showOriginal) {
      GLES20.glUniform1f(sharpenHandle, 0);
    } else {
      GLES20.glUniform1f(sharpenHandle, getSharpenValue());
    }
    GLES20.glUniform1f(sharpenWidthHandle, renderBufferWidth);
    GLES20.glUniform1f(sharpenHeightHandle, renderBufferHeight);
    GLES20.glEnableVertexAttribArray(sharpenInputTexCoordHandle);
    GLES20.glVertexAttribPointer(sharpenInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
    GLES20.glEnableVertexAttribArray(sharpenPositionHandle);
    GLES20.glVertexAttribPointer(sharpenPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  }

  private void drawCustomParamsPass() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
    GLES20.glClear(0);

    GLES20.glUseProgram(toolsShaderProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
    GLES20.glUniform1i(sourceImageHandle, 0);
    if (showOriginal) {
      GLES20.glUniform1f(shadowsHandle, 1);
      GLES20.glUniform1f(highlightsHandle, 1);
      GLES20.glUniform1f(exposureHandle, 0);
      GLES20.glUniform1f(contrastHandle, 1);
      GLES20.glUniform1f(saturationHandle, 1);
      GLES20.glUniform1f(warmthHandle, 0);
      GLES20.glUniform1f(vignetteHandle, 0);
      GLES20.glUniform1f(grainHandle, 0);
      GLES20.glUniform1f(fadeAmountHandle, 0);
      GLES20.glUniform3f(highlightsTintColorHandle, 0, 0, 0);
      GLES20.glUniform1f(highlightsTintIntensityHandle, 0);
      GLES20.glUniform3f(shadowsTintColorHandle, 0, 0, 0);
      GLES20.glUniform1f(shadowsTintIntensityHandle, 0);
      GLES20.glUniform1f(skipToneHandle, 1);
    } else {
      final int tintHighlightsColorId = currentState.getValue(FiltersState.KEY_HIGHLIGHTS_COLOR_ID);
      final int tintShadowsColorId = currentState.getValue(FiltersState.KEY_SHADOWS_COLOR_ID);
      final int tintHighlightsColor = tintHighlightsColorId != 0 ? Theme.getColor(tintHighlightsColorId) : 0;
      final int tintShadowsColor = tintShadowsColorId != 0 ? Theme.getColor(tintShadowsColorId) : 0;
      GLES20.glUniform1f(shadowsHandle, getShadowsValue());
      GLES20.glUniform1f(highlightsHandle, getHighlightsValue());
      GLES20.glUniform1f(exposureHandle, getExposureValue());
      GLES20.glUniform1f(contrastHandle, getContrastValue());
      GLES20.glUniform1f(saturationHandle, getSaturationValue());
      GLES20.glUniform1f(warmthHandle, getWarmthValue());
      GLES20.glUniform1f(vignetteHandle, getVignetteValue());
      GLES20.glUniform1f(grainHandle, getGrainValue());
      GLES20.glUniform1f(fadeAmountHandle, getFadeValue());
      GLES20.glUniform3f(highlightsTintColorHandle, (tintHighlightsColor >> 16 & 0xff) / 255.0f, (tintHighlightsColor >> 8 & 0xff) / 255.0f, (tintHighlightsColor & 0xff) / 255.0f);
      GLES20.glUniform1f(highlightsTintIntensityHandle, getTintHighlightsIntensityValue());
      GLES20.glUniform3f(shadowsTintColorHandle, (tintShadowsColor >> 16 & 0xff) / 255.0f, (tintShadowsColor >> 8 & 0xff) / 255.0f, (tintShadowsColor & 0xff) / 255.0f);
      GLES20.glUniform1f(shadowsTintIntensityHandle, getTintShadowsIntensityValue());
      boolean skipTone = shouldSkipCurvesPass();
      GLES20.glUniform1f(skipToneHandle, skipTone ? 1.0f : 0.0f);
      if (!skipTone) {
        fillCurvesBuffer();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, curveTextures[0]);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 200, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, getCurveBuffer());
        GLES20.glUniform1i(curvesImageHandle, 1);
      }
    }

    GLES20.glUniform1f(widthHandle, renderBufferWidth);
    //noinspection SuspiciousNameCombination
    GLES20.glUniform1f(heightHandle, renderBufferHeight);
    GLES20.glEnableVertexAttribArray(inputTexCoordHandle);
    GLES20.glVertexAttribPointer(inputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  }

  private boolean drawBlurPass() {
    int blurType = currentState.getValue(FiltersState.KEY_BLUR_TYPE);

    if (showOriginal || blurType == FiltersState.BLUR_TYPE_NONE) {
      return false;
    }
    if (needUpdateBlurTexture) {
      GLES20.glUseProgram(blurShaderProgram);
      GLES20.glUniform1i(blurSourceImageHandle, 0);
      GLES20.glEnableVertexAttribArray(blurInputTexCoordHandle);
      GLES20.glVertexAttribPointer(blurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
      GLES20.glEnableVertexAttribArray(blurPositionHandle);
      GLES20.glVertexAttribPointer(blurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
      GLES20.glClear(0);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
      GLES20.glUniform1f(blurWidthHandle, 0.0f);
      GLES20.glUniform1f(blurHeightHandle, 1.0f / renderBufferHeight);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[2]);
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[2], 0);
      GLES20.glClear(0);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
      GLES20.glUniform1f(blurWidthHandle, 1.0f / renderBufferWidth);
      GLES20.glUniform1f(blurHeightHandle, 0.0f);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      needUpdateBlurTexture = false;
    }

    float blurExcludeSize = currentState.getBlurExcludeSize();
    float blurExcludeBlurSize = currentState.getBlurExcludeBlurSize();
    float blurExcludeX = currentState.getBlurExcludeX();
    float blurExcludeY = currentState.getBlurExcludeY();

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
    GLES20.glClear(0);
    if (blurType == FiltersState.BLUR_TYPE_RADIAL) {
      GLES20.glUseProgram(radialBlurShaderProgram);
      GLES20.glUniform1i(radialBlurSourceImageHandle, 0);
      GLES20.glUniform1i(radialBlurSourceImage2Handle, 1);
      GLES20.glUniform1f(radialBlurExcludeSizeHandle, blurExcludeSize);
      GLES20.glUniform1f(radialBlurExcludeBlurSizeHandle, blurExcludeBlurSize);
      GLES20.glUniform2f(radialBlurExcludePointHandle, blurExcludeX, blurExcludeY);
      GLES20.glUniform1f(radialBlurAspectRatioHandle, (float) renderBufferHeight / (float) renderBufferWidth);
      GLES20.glEnableVertexAttribArray(radialBlurInputTexCoordHandle);
      GLES20.glVertexAttribPointer(radialBlurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
      GLES20.glEnableVertexAttribArray(radialBlurPositionHandle);
      GLES20.glVertexAttribPointer(radialBlurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
    } else if (blurType == FiltersState.BLUR_TYPE_LINEAR) {
      GLES20.glUseProgram(linearBlurShaderProgram);
      GLES20.glUniform1i(linearBlurSourceImageHandle, 0);
      GLES20.glUniform1i(linearBlurSourceImage2Handle, 1);
      GLES20.glUniform1f(linearBlurExcludeSizeHandle, blurExcludeSize);
      GLES20.glUniform1f(linearBlurExcludeBlurSizeHandle, blurExcludeBlurSize);
      GLES20.glUniform1f(linearBlurAngleHandle, currentState.getBlurAngle());
      GLES20.glUniform2f(linearBlurExcludePointHandle, blurExcludeX, blurExcludeY);
      GLES20.glUniform1f(linearBlurAspectRatioHandle, (float) renderBufferHeight / (float) renderBufferWidth);
      GLES20.glEnableVertexAttribArray(linearBlurInputTexCoordHandle);
      GLES20.glVertexAttribPointer(linearBlurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
      GLES20.glEnableVertexAttribArray(linearBlurPositionHandle);
      GLES20.glVertexAttribPointer(linearBlurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
    }

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[2]);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    return true;
  }
}
