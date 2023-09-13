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
 * File created on 18/07/2018
 */

#ifndef CHALLEGRAM_TGVOIP_CONFIG_H
#define CHALLEGRAM_TGVOIP_CONFIG_H

#define TGVOIP_FUNC(RETURN_TYPE, NAME, ...)          \
  extern "C" {                                       \
  JNIEXPORT RETURN_TYPE                              \
      Java_org_thunderdog_challegram_voip_##NAME(       \
          JNIEnv *env, ##__VA_ARGS__);               \
  }                                                  \
  JNIEXPORT RETURN_TYPE                              \
      Java_org_thunderdog_challegram_voip_##NAME(       \
          JNIEnv *env, ##__VA_ARGS__)

#endif //CHALLEGRAM_TGVOIP_CONFIG_H
